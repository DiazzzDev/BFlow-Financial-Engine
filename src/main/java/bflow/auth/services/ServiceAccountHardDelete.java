package bflow.auth.services;

import bflow.auth.SystemUsers;
import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.budget.repository.RepositoryBudget;
import bflow.common.aws.service.StorageService;
import bflow.expenses.RepositoryExpense;
import bflow.income.RepositoryIncome;
import bflow.receipts.repository.RepositoryReceiptUpload;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.storage.repository.RepositoryStoredFile;
import bflow.subscription.entities.Payment;
import bflow.subscription.repository.RepositoryPayment;
import bflow.subscription.repository.RepositorySubscription;
import bflow.tranfers.RepositoryTransfers;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Permanently removes a PENDING_DELETION account once its 30-day
 * grace period has elapsed.
 *
 * <p>Shared-wallet contributions ({@code Expense}/{@code Income}/
 * {@code Transfer}) and billing records ({@code Subscription}/
 * {@code Payment}) are reassigned to {@link SystemUsers#GHOST_USER_ID}
 * so other members' history and the account's payment trail survive.
 * {@code Budget} and {@code RecurringTransaction} are always deleted
 * — personal configuration, not shared history — regardless of
 * whether the wallet is shared. Wallets where the user was the sole
 * member are deleted entirely, along with every row that references
 * them; there is no {@code ON DELETE CASCADE} on any of these FKs
 * (checked V1__baseline.sql), so the order below matters.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceAccountHardDelete {

    private final RepositoryUser repositoryUser;
    private final RepositoryWalletUser repositoryWalletUser;
    private final RepositoryWallet repositoryWallet;
    private final RepositoryExpense repositoryExpense;
    private final RepositoryIncome repositoryIncome;
    private final RepositoryTransfers repositoryTransfers;
    private final RepositoryBudget repositoryBudget;
    private final RepositoryRecurringTransaction repositoryRecurring;
    private final RepositoryReceiptUpload repositoryReceiptUpload;
    private final RepositoryStoredFile repositoryStoredFile;
    private final RepositorySubscription repositorySubscription;
    private final RepositoryPayment repositoryPayment;
    private final StorageService storageService;

    /**
     * Hard-deletes the given user.
     * @param user the user to hard-delete (must be PENDING_DELETION)
     */
    @Transactional
    public void hardDelete(final User user) {

        User ghost = repositoryUser.findById(SystemUsers.GHOST_USER_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "System ghost user is missing — check "
                                + "V33__ghost_user_and_billing_snapshot.sql"
                ));

        List<WalletUser> memberships =
                repositoryWalletUser.findByUserId(user.getId());

        List<UUID> sharedWalletIds = memberships.stream()
                .filter(m -> repositoryWalletUser
                        .countByWalletId(m.getWallet().getId()) > 1)
                .map(m -> m.getWallet().getId())
                .toList();

        List<UUID> personalWalletIds = memberships.stream()
                .filter(m -> repositoryWalletUser
                        .countByWalletId(m.getWallet().getId()) == 1)
                .map(m -> m.getWallet().getId())
                .toList();

        // 1. Budgets and recurring transactions: always deleted,
        //    regardless of wallet sharing — personal configuration.
        repositoryBudget.deleteByUserId(user.getId());
        repositoryRecurring.deleteByUserId(user.getId());

        // 2. Shared wallets: reassign contributions to the ghost user.
        if (!sharedWalletIds.isEmpty()) {
            repositoryExpense.reassignContributor(
                    user.getId(), ghost, sharedWalletIds);
            repositoryIncome.reassignContributor(
                    user.getId(), ghost, sharedWalletIds);
            repositoryTransfers.reassignContributor(
                    user.getId(), ghost, sharedWalletIds);
            repositoryReceiptUpload.reassignUser(
                    user.getId(), ghost, sharedWalletIds);
            repositoryStoredFile.reassignReceiptOwnersForWallets(
                    user.getId(), ghost, sharedWalletIds);
        }

        // 3. Personal wallets: delete everything, in FK-safe order.
        if (!personalWalletIds.isEmpty()) {
            deletePersonalWalletData(user.getId(), personalWalletIds);
        }

        // 4. Remove wallet memberships (both shared and personal —
        //    for shared wallets this just drops the user's own row).
        for (WalletUser membership : memberships) {
            repositoryWalletUser.delete(membership);
        }

        // 5. Now safe: delete the personal wallets themselves.
        if (!personalWalletIds.isEmpty()) {
            repositoryWallet.deleteAllById(personalWalletIds);
        }

        // 6. Billing records: snapshot the real email, then reassign
        //    the FK to the ghost user.
        reassignBillingRecords(user, ghost);

        // 7. Finally, the user row itself.
        repositoryUser.delete(user);

        log.info(
                "Hard-deleted user {} — {} shared wallet(s) reassigned, "
                        + "{} personal wallet(s) removed",
                user.getId(), sharedWalletIds.size(), personalWalletIds.size()
        );
    }

    private void deletePersonalWalletData(
            final UUID userId,
            final List<UUID> walletIds
    ) {
        // Collect S3 keys BEFORE deleting the expenses/incomes that
        // reference them — the receiptFile join is only resolvable
        // while those rows still exist.
        List<String> receiptObjectKeys = repositoryStoredFile
                .findReceiptObjectKeysForWallets(userId, walletIds);

        repositoryTransfers.deleteByWalletIds(walletIds);
        repositoryExpense.deleteByWalletIdIn(walletIds);
        repositoryIncome.deleteByWalletIdIn(walletIds);
        repositoryReceiptUpload.deleteByWalletIdIn(walletIds);

        if (!receiptObjectKeys.isEmpty()) {
            repositoryStoredFile.deleteByObjectKeyIn(receiptObjectKeys);
            receiptObjectKeys.forEach(storageService::delete);
        }
    }

    private void reassignBillingRecords(final User user, final User ghost) {

        repositorySubscription.findByUserId(user.getId())
                .ifPresent(sub -> {
                    sub.setBillingEmail(user.getEmail());
                    sub.setUser(ghost);
                    repositorySubscription.save(sub);
                });

        List<Payment> payments =
                repositoryPayment.findByUserId(user.getId());

        for (Payment payment : payments) {
            payment.setBillingEmail(user.getEmail());
            payment.setUser(ghost);
        }

        repositoryPayment.saveAll(payments);
    }
}