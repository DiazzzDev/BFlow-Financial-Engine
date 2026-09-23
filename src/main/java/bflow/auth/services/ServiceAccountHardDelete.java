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
import bflow.subscription.entities.Subscription;
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
    private final CognitoAdminService cognitoAdminService;

    /**
     * Hard-deletes the given user.
     * @param user the user to hard-delete (must be PENDING_DELETION)
     */
    @Transactional   
    public void hardDelete(final User user) {

        UUID userId = user.getId(); // guarda el id antes de que el objeto se desacople

        User ghost = repositoryUser.findById(SystemUsers.GHOST_USER_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "System ghost user is missing — check "
                                + "V33__ghost_user_and_billing_snapshot.sql"
                ));

        List<WalletUser> memberships =
                repositoryWalletUser.findByUserId(userId);

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

        repositoryBudget.deleteByUserId(userId);
        repositoryRecurring.deleteByUserId(userId);

        if (!sharedWalletIds.isEmpty()) {
            repositoryExpense.reassignContributor(userId, ghost, sharedWalletIds);
            repositoryIncome.reassignContributor(userId, ghost, sharedWalletIds);
            repositoryTransfers.reassignContributor(userId, ghost, sharedWalletIds);
            repositoryReceiptUpload.reassignUser(userId, ghost, sharedWalletIds);
            repositoryStoredFile.reassignReceiptOwnersForWallets(
                    userId, ghost, sharedWalletIds);
        }

        if (!personalWalletIds.isEmpty()) {
            deletePersonalWalletData(userId, personalWalletIds);
        }

        // memberships puede estar desacoplado tras los clears de arriba —
        // vuelve a resolverlo por id antes de borrar.
        repositoryWalletUser.deleteByUserId(userId);

        if (!personalWalletIds.isEmpty()) {
            repositoryWallet.deleteAllById(personalWalletIds);
        }

        // re-fetch: ghost y user pudieron quedar detached por los clears.
        User freshGhost = repositoryUser.findById(SystemUsers.GHOST_USER_ID)
                .orElseThrow();
        reassignBillingRecords(userId, freshGhost);

        User freshUser = repositoryUser.findById(userId).orElseThrow();

        //Deletes user from cognito pool
        cognitoAdminService.deleteUser(freshUser.getCognitoSub());

        repositoryUser.delete(freshUser);

        log.info(
                "Hard-deleted user {} — {} shared wallet(s) reassigned, "
                        + "{} personal wallet(s) removed",
                userId, sharedWalletIds.size(), personalWalletIds.size()
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

    private void reassignBillingRecords(final UUID userId, final User ghost) {

        String email = repositoryUser.findById(userId)
                .map(User::getEmail)
                .orElseThrow();

        List<Subscription> subscriptions =
                repositorySubscription.findAllByUserId(userId);

        for (Subscription sub : subscriptions) {
            sub.setBillingEmail(email);
            sub.setUser(ghost);
        }
        repositorySubscription.saveAll(subscriptions);

        List<Payment> payments = repositoryPayment.findAllByUserId(userId);

        for (Payment payment : payments) {
            payment.setBillingEmail(email);
            payment.setUser(ghost);
        }
        repositoryPayment.saveAll(payments);
    }
}