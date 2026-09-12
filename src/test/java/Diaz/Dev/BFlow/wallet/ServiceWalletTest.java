package Diaz.Dev.BFlow.wallet;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.expenses.RepositoryExpense;
import bflow.income.RepositoryIncome;
import bflow.common.i18n.MessageService;
import bflow.tranfers.RepositoryTransfers;
import bflow.wallet.DTO.UpdateWalletRequest;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ServiceWallet.
 */
@ExtendWith(MockitoExtension.class)
class ServiceWalletTest {

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private UserService userService;

    @Mock
    private RepositoryExpense repositoryExpense;

    @Mock
    private RepositoryIncome repositoryIncome;

    @Mock
    private RepositoryTransfers repositoryTransfers;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private ServiceWallet serviceWallet;

    private UUID userId;
    private UUID walletId;
    private User user;
    private Wallet wallet;
    private WalletUser walletUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setStatus(UserStatus.ACTIVE);

        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Test Wallet");
        wallet.setDescription("Test description");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(BigDecimal.valueOf(1000));
        wallet.setInitialValue(BigDecimal.valueOf(1000));
        wallet.setCreatedAt(Instant.now());

        walletUser = new WalletUser();
        walletUser.setUser(user);
        walletUser.setWallet(wallet);
        walletUser.setRole(WalletRole.OWNER);
    }

    @Test
    void testGetWalletById() {
        // Arrange
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));

        // Act
        WalletResponse result = serviceWallet.getWalletById(walletId, userId);

        // Assert
        assertEquals(walletId, result.getId());
        assertEquals("Test Wallet", result.getName());
        verify(repositoryWalletUser).findByWalletIdAndUserId(walletId, userId);
    }

    @Test
    void testGetWalletByIdAccessDenied() {
        // Arrange
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> serviceWallet.getWalletById(walletId, userId));
    }

    @Test
    void testPatchWalletAccessDenied() {
        // Arrange
        WalletUser memberUser = new WalletUser();
        memberUser.setUser(user);
        memberUser.setWallet(wallet);
        memberUser.setRole(WalletRole.MEMBER);

        UpdateWalletRequest request = new UpdateWalletRequest();
        request.setName("New Name");

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(memberUser));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> serviceWallet.patchWallet(walletId, request, userId));
    }

    // ---- addBalance ----

    @Test
    void addBalance_positiveAmount_increasesBalance() {
        serviceWallet.addBalance(wallet, BigDecimal.valueOf(250));

        assertEquals(BigDecimal.valueOf(1250), wallet.getBalance());
    }

    @Test
    void addBalance_zeroAmount_isANoOp() {
        serviceWallet.addBalance(wallet, BigDecimal.ZERO);

        assertEquals(BigDecimal.valueOf(1000), wallet.getBalance());
    }

    @Test
    void addBalance_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.addBalance(
                        wallet, BigDecimal.valueOf(-1)));

        // Balance must be untouched — the guard runs before mutation.
        assertEquals(BigDecimal.valueOf(1000), wallet.getBalance());
    }

    // ---- subtractBalance ----

    @Test
    void subtractBalance_leavesExactlyZero_isAllowed() {
        // The boundary case: signum() < 0 means exactly zero must be
        // accepted, not rejected as "insufficient".
        serviceWallet.subtractBalance(wallet, BigDecimal.valueOf(1000));

        assertEquals(BigDecimal.ZERO.setScale(0),
                wallet.getBalance().setScale(0));
    }

    @Test
    void subtractBalance_oneCentOverBalance_throwsInsufficientBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.subtractBalance(
                        wallet, BigDecimal.valueOf(1000.01)));

        // Balance must be untouched on rejection.
        assertEquals(BigDecimal.valueOf(1000), wallet.getBalance());
    }

    @Test
    void subtractBalance_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.subtractBalance(
                        wallet, BigDecimal.valueOf(-50)));
    }

    // ---- adjustBalanceForExpenseUpdate ----
    // Direction is the mirror of income: a HIGHER expense amount
    // must DECREASE the balance (spent more), a LOWER one increases
    // it. This is the exact bug the earlier shared method had.

    @Test
    void adjustBalanceForExpenseUpdate_increasingAmount_subtractsDifference() {
        // Editing an existing $100 expense up to $150 should only
        // pull the extra $50 from the wallet, not re-charge $150.
        serviceWallet.adjustBalanceForExpenseUpdate(
                wallet, BigDecimal.valueOf(100), BigDecimal.valueOf(150));

        assertEquals(BigDecimal.valueOf(950), wallet.getBalance());
    }

    @Test
    void adjustBalanceForExpenseUpdate_decreasingAmount_refundsDifference() {
        serviceWallet.adjustBalanceForExpenseUpdate(
                wallet, BigDecimal.valueOf(150), BigDecimal.valueOf(100));

        assertEquals(BigDecimal.valueOf(1050), wallet.getBalance());
    }

    @Test
    void adjustBalanceForExpenseUpdate_sameAmount_isANoOp() {
        serviceWallet.adjustBalanceForExpenseUpdate(
                wallet, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

        assertEquals(BigDecimal.valueOf(1000), wallet.getBalance());
    }

    @Test
    void adjustBalanceForExpenseUpdate_negativeOldAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.adjustBalanceForExpenseUpdate(
                        wallet, BigDecimal.valueOf(-10),
                        BigDecimal.valueOf(50)));
    }

    @Test
    void adjustBalanceForExpenseUpdate_negativeNewAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.adjustBalanceForExpenseUpdate(
                        wallet, BigDecimal.valueOf(50),
                        BigDecimal.valueOf(-10)));
    }

    @Test
    void adjustBalanceForExpenseUpdate_resultingBalanceNegative_throwsAndDoesNotMutate() {
        // Wallet has 1000. Old expense was 50, new one is 2000 —
        // adjustment would need 2000 - 50 = 1950 more than exists.
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.adjustBalanceForExpenseUpdate(
                        wallet, BigDecimal.valueOf(50),
                        BigDecimal.valueOf(2000)));

        assertEquals(BigDecimal.valueOf(1000), wallet.getBalance());
    }

    // ---- adjustBalanceForIncomeUpdate ----
    // Same-direction math: a HIGHER income amount increases the
    // balance, a LOWER one decreases it.

    @Test
    void adjustBalanceForIncomeUpdate_increasingAmount_addsDifference() {
        serviceWallet.adjustBalanceForIncomeUpdate(
                wallet, BigDecimal.valueOf(100), BigDecimal.valueOf(150));

        assertEquals(BigDecimal.valueOf(1050), wallet.getBalance());
    }

    @Test
    void adjustBalanceForIncomeUpdate_decreasingAmount_subtractsDifference() {
        serviceWallet.adjustBalanceForIncomeUpdate(
                wallet, BigDecimal.valueOf(150), BigDecimal.valueOf(100));

        assertEquals(BigDecimal.valueOf(950), wallet.getBalance());
    }

    @Test
    void adjustBalanceForIncomeUpdate_sameAmount_isANoOp() {
        serviceWallet.adjustBalanceForIncomeUpdate(
                wallet, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

        assertEquals(BigDecimal.valueOf(1000), wallet.getBalance());
    }

    @Test
    void adjustBalanceForIncomeUpdate_negativeOldAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.adjustBalanceForIncomeUpdate(
                        wallet, BigDecimal.valueOf(-10),
                        BigDecimal.valueOf(50)));
    }

    @Test
    void adjustBalanceForIncomeUpdate_resultingBalanceNegative_throwsAndDoesNotMutate() {
        // Wallet has 1000. Old income was 950, being edited down to 0
        // would need to remove 950 from a wallet that only has 1000
        // — fine on its own, but combined with a much larger prior
        // withdrawal this boundary still needs to hold at zero.
        wallet.setBalance(BigDecimal.valueOf(500));

        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.adjustBalanceForIncomeUpdate(
                        wallet, BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(0)));

        assertEquals(BigDecimal.valueOf(500), wallet.getBalance());
    }

    // ---- reverseTransactionImpact ----

    @Test
    void reverseTransactionImpact_addsAmountBackToBalance() {
        // Used when an expense is deleted: the money "comes back".
        serviceWallet.reverseTransactionImpact(
                wallet, BigDecimal.valueOf(200));

        assertEquals(BigDecimal.valueOf(1200), wallet.getBalance());
    }

    @Test
    void reverseTransactionImpact_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> serviceWallet.reverseTransactionImpact(
                        wallet, BigDecimal.valueOf(-5)));
    }

    @Test
    void deleteWallet_notOwner_throwsAccessDeniedAndTouchesNothing() {
        WalletUser memberUser = new WalletUser();
        memberUser.setUser(user);
        memberUser.setWallet(wallet);
        memberUser.setRole(WalletRole.MEMBER);

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(memberUser));

        assertThrows(AccessDeniedException.class,
                () -> serviceWallet.deleteWallet(walletId, userId));

        verify(repositoryWallet, never()).delete(any(Wallet.class));
        verify(repositoryExpense, never()).countByWalletId(any(UUID.class));
    }

    @Test
    void deleteWallet_noAccess_throwsAccessDenied() {
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> serviceWallet.deleteWallet(walletId, userId));
    }
}
