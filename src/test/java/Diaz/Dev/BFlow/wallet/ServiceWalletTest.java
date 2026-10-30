package Diaz.Dev.BFlow.wallet;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.common.i18n.MessageService;
import bflow.recurring.RepositoryRecurringTransaction;
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
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.DTO.WalletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private RepositoryRecurringTransaction repositoryRecurringTransaction;

    @Mock
    private PlanLimitService planLimitService;

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
        walletUser.setDefaultWallet(true);
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
        assertEquals(true, result.isDefaultWallet());
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
    void createWallet_pendingDeletionUserIsRejectedBeforeWalletQueries() {
        user.setStatus(UserStatus.PENDING_DELETION);
        org.mockito.Mockito.doThrow(new IllegalStateException("inactive"))
                .when(userService).validateUserActive(userId);

        assertThrows(IllegalStateException.class,
                () -> serviceWallet.createWallet(null, userId));

        verify(repositoryWalletUser, never())
                .countByUserIdAndRole(any(), any());
        verify(repositoryWallet, never()).saveAndFlush(any());
    }

    @Test
    void createWallet_roundsOpeningBalanceAndCreatesOwnerMembership() {
        WalletRequest request = new WalletRequest();
        request.setName("  Emergency fund  ");
        request.setDescription("  Savings reserve  ");
        request.setCurrency(Currency.USD);
        request.setInitialValue(new BigDecimal("12.346"));

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.countByUserIdAndRole(userId, WalletRole.OWNER))
                .thenReturn(0L);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWallet.saveAndFlush(any(Wallet.class)))
                .thenAnswer(invocation -> {
                    Wallet saved = invocation.getArgument(0);
                    saved.setId(walletId);
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });
        when(repositoryWalletUser.countByWalletId(walletId)).thenReturn(1L);

        WalletResponse response = serviceWallet.createWallet(request, userId);

        assertEquals(walletId, response.getId());
        assertEquals("Emergency fund", response.getName());
        assertEquals("  Savings reserve  ", response.getDescription());
        assertEquals(new BigDecimal("12.35"), response.getBalance());
        ArgumentCaptor<Wallet> savedWallet = ArgumentCaptor.forClass(Wallet.class);
        verify(repositoryWallet).saveAndFlush(savedWallet.capture());
        assertEquals(new BigDecimal("12.35"), savedWallet.getValue().getInitialValue());
        verify(planLimitService).assertCanCreate(
                userId, bflow.subscription.FeatureCodes.WALLETS, 0L);
        ArgumentCaptor<WalletUser> membership =
                ArgumentCaptor.forClass(WalletUser.class);
        verify(repositoryWalletUser).save(membership.capture());
        assertEquals(user, membership.getValue().getUser());
        assertEquals(WalletRole.OWNER, membership.getValue().getRole());
    }

    @Test
    void getWalletInfo_withoutHistoryReturnsNullActivityAndZeroCount() {
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryExpense.findMaxCreatedAtByWalletId(walletId)).thenReturn(null);
        when(repositoryIncome.findMaxCreatedAtByWalletId(walletId)).thenReturn(null);
        when(repositoryTransfers.findMaxCreatedAtByWallet(walletId)).thenReturn(null);
        when(repositoryExpense.findTopByWalletIdOrderByAmountDesc(walletId))
                .thenReturn(Optional.empty());
        when(repositoryExpense.countByWalletId(walletId)).thenReturn(0L);
        when(repositoryIncome.countByWalletId(walletId)).thenReturn(0L);
        when(repositoryTransfers.countByWallet(walletId)).thenReturn(0L);
        when(repositoryRecurringTransaction
                .findByWalletIdAndActiveTrueOrderByNextExecutionDateAsc(
                        any(), any())).thenReturn(java.util.List.of());

        bflow.wallet.DTO.WalletInfoResponse response =
                serviceWallet.getWalletInfo(walletId, userId);

        assertNull(response.getLastActivity());
        assertNull(response.getHighestExpense());
        assertEquals(0L, response.getTransactions());
        assertEquals(wallet.getInitialValue(), response.getInitialValue());
        assertEquals(java.util.List.of(), response.getUpcoming());
    }

    @Test
    void getWalletInfo_selectsLatestActivityAndCountsAllTransactionTypes() {
        Instant expenseAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant incomeAt = Instant.parse("2026-10-03T10:00:00Z");
        Instant transferAt = Instant.parse("2026-10-02T10:00:00Z");
        Expense highestExpense = new Expense();
        highestExpense.setTitle("Rent");
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryExpense.findMaxCreatedAtByWalletId(walletId))
                .thenReturn(expenseAt);
        when(repositoryIncome.findMaxCreatedAtByWalletId(walletId))
                .thenReturn(incomeAt);
        when(repositoryTransfers.findMaxCreatedAtByWallet(walletId))
                .thenReturn(transferAt);
        when(repositoryExpense.findTopByWalletIdOrderByAmountDesc(walletId))
                .thenReturn(Optional.of(highestExpense));
        when(repositoryExpense.countByWalletId(walletId)).thenReturn(2L);
        when(repositoryIncome.countByWalletId(walletId)).thenReturn(3L);
        when(repositoryTransfers.countByWallet(walletId)).thenReturn(4L);
        when(repositoryRecurringTransaction
                .findByWalletIdAndActiveTrueOrderByNextExecutionDateAsc(
                        any(), any())).thenReturn(java.util.List.of());

        bflow.wallet.DTO.WalletInfoResponse response =
                serviceWallet.getWalletInfo(walletId, userId);

        assertEquals(incomeAt, response.getLastActivity());
        assertEquals("Rent", response.getHighestExpense());
        assertEquals(9L, response.getTransactions());
    }

    @Test
    void createDefaultWallet_createsUsdOwnerDefaultWhenNoWalletExists() {
        when(repositoryWalletUser.existsByUserId(userId)).thenReturn(false);
        when(repositoryWallet.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repositoryWalletUser.save(any(WalletUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Wallet created = serviceWallet.createDefaultWallet(user);

        assertEquals("Personal", created.getName());
        assertEquals("Default wallet", created.getDescription());
        assertEquals(Currency.USD, created.getCurrency());
        assertEquals(BigDecimal.ZERO, created.getBalance());
        ArgumentCaptor<WalletUser> membership =
                ArgumentCaptor.forClass(WalletUser.class);
        verify(repositoryWalletUser).save(membership.capture());
        assertEquals(user, membership.getValue().getUser());
        assertEquals(WalletRole.OWNER, membership.getValue().getRole());
        assertTrue(membership.getValue().isDefaultWallet());
    }

    @Test
    void createDefaultWallet_returnsExistingOwnerWalletWithoutCreatingAnother() {
        when(repositoryWalletUser.existsByUserId(userId)).thenReturn(true);
        when(repositoryWalletUser
                .findFirstByUserIdAndRoleOrderByDefaultWalletDescCreatedAtAsc(
                        userId, WalletRole.OWNER))
                .thenReturn(Optional.of(walletUser));

        assertEquals(wallet, serviceWallet.createDefaultWallet(user));

        verify(repositoryWallet, never()).save(any());
        verify(repositoryWalletUser, never()).save(any());
    }

    @Test
    void deleteWallet_refusesFinancialHistoryBeforeDeletingMembership() {
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryExpense.countByWalletId(walletId)).thenReturn(1L);

        assertThrows(IllegalStateException.class,
                () -> serviceWallet.deleteWallet(walletId, userId));

        verify(repositoryWalletUser, never()).delete(any(WalletUser.class));
        verify(repositoryWallet, never()).delete(any(Wallet.class));
    }

    @Test
    void deleteWallet_withoutFinancialHistoryDeletesWalletAndMembership() {
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryExpense.countByWalletId(walletId)).thenReturn(0L);
        when(repositoryIncome.countByWalletId(walletId)).thenReturn(0L);
        when(repositoryTransfers.countByWallet(walletId)).thenReturn(0L);
        when(repositoryRecurringTransaction.existsByWalletId(walletId))
                .thenReturn(false);

        serviceWallet.deleteWallet(walletId, userId);

        verify(repositoryWalletUser).delete(walletUser);
        verify(repositoryWallet).delete(wallet);
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

    @Test
    void setOpeningBalance_updatesInitialAndCurrentBalanceByDifference() {
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        serviceWallet.setOpeningBalance(
                walletId, BigDecimal.valueOf(1200), userId);

        assertEquals(new BigDecimal("1200.00"), wallet.getInitialValue());
        assertEquals(new BigDecimal("1200.00"), wallet.getBalance());
        verify(repositoryWallet).save(wallet);
    }

    @Test
    void setDefaultWallet_ownedWalletClearsPreviousAndMarksTarget() {
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));

        serviceWallet.setDefaultWallet(walletId, userId);

        verify(repositoryWalletUser).clearDefault(userId);
        verify(repositoryWalletUser).markDefault(walletId, userId);
    }

    @Test
    void setDefaultWallet_memberWalletIsRejectedWithoutChangingSelection() {
        walletUser.setRole(WalletRole.MEMBER);
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));

        assertThrows(AccessDeniedException.class,
                () -> serviceWallet.setDefaultWallet(walletId, userId));

        verify(repositoryWalletUser, never()).clearDefault(any());
        verify(repositoryWalletUser, never()).markDefault(any(), any());
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
