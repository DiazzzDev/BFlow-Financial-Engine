package Diaz.Dev.BFlow.auth.services;

import bflow.auth.SystemUsers;
import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.CognitoAdminService;
import bflow.auth.services.ServiceAccountHardDelete;
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
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceAccountHardDeleteUnitTest {

    private RepositoryUser repositoryUser;
    private RepositoryWalletUser repositoryWalletUser;
    private RepositoryWallet repositoryWallet;
    private RepositoryExpense repositoryExpense;
    private RepositoryIncome repositoryIncome;
    private RepositoryTransfers repositoryTransfers;
    private RepositoryBudget repositoryBudget;
    private RepositoryRecurringTransaction repositoryRecurring;
    private RepositoryReceiptUpload repositoryReceiptUpload;
    private RepositoryStoredFile repositoryStoredFile;
    private RepositorySubscription repositorySubscription;
    private RepositoryPayment repositoryPayment;
    private StorageService storageService;
    private CognitoAdminService cognitoAdminService;
    private ServiceAccountHardDelete service;
    private User user;
    private User ghost;
    private UUID userId;

    @BeforeEach
    void setUp() {
        repositoryUser = mock(RepositoryUser.class);
        repositoryWalletUser = mock(RepositoryWalletUser.class);
        repositoryWallet = mock(RepositoryWallet.class);
        repositoryExpense = mock(RepositoryExpense.class);
        repositoryIncome = mock(RepositoryIncome.class);
        repositoryTransfers = mock(RepositoryTransfers.class);
        repositoryBudget = mock(RepositoryBudget.class);
        repositoryRecurring = mock(RepositoryRecurringTransaction.class);
        repositoryReceiptUpload = mock(RepositoryReceiptUpload.class);
        repositoryStoredFile = mock(RepositoryStoredFile.class);
        repositorySubscription = mock(RepositorySubscription.class);
        repositoryPayment = mock(RepositoryPayment.class);
        storageService = mock(StorageService.class);
        cognitoAdminService = mock(CognitoAdminService.class);
        service = new ServiceAccountHardDelete(
                repositoryUser,
                repositoryWalletUser,
                repositoryWallet,
                repositoryExpense,
                repositoryIncome,
                repositoryTransfers,
                repositoryBudget,
                repositoryRecurring,
                repositoryReceiptUpload,
                repositoryStoredFile,
                repositorySubscription,
                repositoryPayment,
                storageService,
                cognitoAdminService);
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("leaving@example.com")
                .cognitoSub("cognito-subject")
                .build();
        ghost = User.builder()
                .id(SystemUsers.GHOST_USER_ID)
                .email("deleted@example.com")
                .build();
        when(repositoryUser.findById(SystemUsers.GHOST_USER_ID))
                .thenReturn(Optional.of(ghost));
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByUserId(userId)).thenReturn(List.of());
        when(repositorySubscription.findAllByUserId(userId))
                .thenReturn(List.of());
        when(repositoryPayment.findAllByUserId(userId)).thenReturn(List.of());
    }

    @Test
    void deletesPersonalWalletDataAndReassignsSurvivingBillingHistory() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        WalletUser membership = new WalletUser();
        membership.setWallet(wallet);
        when(repositoryWalletUser.findByUserId(userId))
                .thenReturn(List.of(membership));
        when(repositoryWalletUser.countByWalletId(walletId)).thenReturn(1L);
        when(repositoryStoredFile.findReceiptObjectKeysForWallets(
                userId, List.of(walletId)))
                .thenReturn(List.of("users/receipt/scan.png"));
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setBillingEmail("old@example.com");
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setBillingEmail("old@example.com");
        when(repositorySubscription.findAllByUserId(userId))
                .thenReturn(List.of(subscription));
        when(repositoryPayment.findAllByUserId(userId))
                .thenReturn(List.of(payment));

        service.hardDelete(user);

        verify(repositoryBudget).deleteByUserId(userId);
        verify(repositoryRecurring).deleteByUserId(userId);
        verify(repositoryTransfers).deleteByWalletIds(List.of(walletId));
        verify(repositoryExpense).deleteByWalletIdIn(List.of(walletId));
        verify(repositoryIncome).deleteByWalletIdIn(List.of(walletId));
        verify(repositoryReceiptUpload).deleteByWalletIdIn(List.of(walletId));
        verify(repositoryStoredFile).deleteByObjectKeyIn(
                List.of("users/receipt/scan.png"));
        verify(storageService).delete("users/receipt/scan.png");
        verify(repositoryWalletUser).deleteByUserId(userId);
        verify(repositoryWallet).deleteAllById(List.of(walletId));
        verify(repositoryExpense).reassignAllContributions(userId, ghost);
        verify(repositoryIncome).reassignAllContributions(userId, ghost);
        verify(repositoryTransfers).reassignAllContributions(userId, ghost);
        verify(repositoryReceiptUpload).reassignAllUsers(userId, ghost);
        verify(repositoryStoredFile).reassignAllReceiptOwners(userId, ghost);
        verify(repositorySubscription).saveAll(List.of(subscription));
        verify(repositoryPayment).saveAll(List.of(payment));
        assertEquals("leaving@example.com", subscription.getBillingEmail());
        assertEquals("leaving@example.com", payment.getBillingEmail());
        assertEquals(ghost, subscription.getUser());
        assertEquals(ghost, payment.getUser());
        verify(cognitoAdminService).deleteUser("cognito-subject");
        verify(repositoryUser).delete(user);
    }

    @Test
    void skipsWalletCleanupWhenNoMembershipsExist() {
        service.hardDelete(user);

        verify(repositoryWalletUser).deleteByUserId(userId);
        verify(repositoryWallet, never()).deleteAllById(anyList());
        verify(repositoryStoredFile, never())
                .findReceiptObjectKeysForWallets(userId, List.of());
        verify(cognitoAdminService).deleteUser("cognito-subject");
    }

    @Test
    void refusesToDeleteWhenSystemGhostUserIsMissing() {
        when(repositoryUser.findById(SystemUsers.GHOST_USER_ID))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.hardDelete(user));

        verify(repositoryWalletUser, never()).findByUserId(userId);
        verify(repositoryUser, never()).delete(user);
    }
}
