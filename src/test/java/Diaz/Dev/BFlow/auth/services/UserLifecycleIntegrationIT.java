package Diaz.Dev.BFlow.auth.services;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.aws.service.SesEmailService;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** User lifecycle integration coverage against the PostgreSQL test service. */
@SpringBootTest(classes = bflow.BFlowApplication.class)
@ActiveProfiles("test")
@Transactional
class UserLifecycleIntegrationIT {

    @Autowired private RepositoryUser users;
    @Autowired private RepositoryWallet wallets;
    @Autowired private RepositoryWalletUser walletUsers;
    @Autowired private RepositoryExpense expenses;
    @Autowired private UserService userService;

    @MockitoBean private SesEmailService sesEmailService;

    private User user;

    @BeforeEach
    void createActiveUser() {
        user = new User();
        user.setEmail("lifecycle-" + UUID.randomUUID() + "@example.test");
        user.setName("Lifecycle integration");
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        user = users.saveAndFlush(user);
    }

    @Test
    void softDeletePersistsPendingStateAndTimestamp() {
        userService.softDelete(user.getId());

        User persisted = users.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.PENDING_DELETION, persisted.getStatus());
        assertNotNull(persisted.getDeletionRequestedAt());
    }

    @Test
    void cancellationRestoresActiveStateAndClearsDeletionTimestamp() {
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now().minusSeconds(30));
        users.saveAndFlush(user);

        userService.cancelDeletion(user.getId());

        User persisted = users.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.ACTIVE, persisted.getStatus());
        assertNull(persisted.getDeletionRequestedAt());
    }

    @Test
    void activeUserCannotCancelADeletionThatWasNotRequested() {
        assertThrows(IllegalStateException.class,
                () -> userService.cancelDeletion(user.getId()));

        assertEquals(UserStatus.ACTIVE,
                users.findById(user.getId()).orElseThrow().getStatus());
    }

    @Test
    void pendingDeletionUserCannotRequestDeletionAgain() {
        user.setStatus(UserStatus.PENDING_DELETION);
        Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
        user.setDeletionRequestedAt(requestedAt);
        users.saveAndFlush(user);

        assertThrows(IllegalStateException.class,
                () -> userService.softDelete(user.getId()));

        assertEquals(requestedAt,
                users.findById(user.getId()).orElseThrow()
                        .getDeletionRequestedAt());
    }

    @Test
    void pendingDeletionKeepsWalletAndSharedFinancialHistoryDuringGracePeriod() {
        Wallet wallet = new Wallet();
        wallet.setName("Lifecycle wallet");
        wallet.setDescription("Retained during deletion grace period");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(new BigDecimal("40.00"));
        wallet.setInitialValue(new BigDecimal("50.00"));
        wallet = wallets.saveAndFlush(wallet);

        WalletUser membership = new WalletUser();
        membership.setUser(user);
        membership.setWallet(wallet);
        membership.setRole(WalletRole.OWNER);
        membership.setDefaultWallet(true);
        walletUsers.saveAndFlush(membership);

        Expense expense = new Expense();
        expense.setTitle("Retained expense");
        expense.setAmount(new BigDecimal("10.00"));
        expense.setDate(LocalDate.now());
        expense.setWallet(wallet);
        expense.setContributor(user);
        expense.setSource("manual");
        expense = expenses.saveAndFlush(expense);

        userService.softDelete(user.getId());

        assertTrue(wallets.findById(wallet.getId()).isPresent());
        assertTrue(walletUsers.existsByWalletIdAndUserId(
                wallet.getId(), user.getId()));
        assertTrue(expenses.findById(expense.getId()).isPresent());
    }
}
