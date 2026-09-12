package Diaz.Dev.BFlow.expenses;

import bflow.auth.entities.User;
import bflow.auth.services.UserService;
import bflow.budget.services.BudgetService;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.i18n.MessageService;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.DTO.QuickExpenseRequest;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.expenses.services.QuickExpenseService;
import bflow.merchant.MerchantDetectionService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QuickExpenseService}, focused on the wallet
 * balance fix: a quick expense must debit the wallet at creation
 * time, using a locked wallet read, exactly like a normal expense
 * does — otherwise a later edit or delete on the same record can
 * mint balance that was never actually subtracted.
 */
@ExtendWith(MockitoExtension.class)
class QuickExpenseServiceTest {

    @Mock
    private RepositoryExpense repositoryExpense;

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserService userService;

    @Mock
    private MerchantDetectionService merchantDetectionService;

    @Mock
    private RepositoryWalletUser walletUserRepository;

    @Mock
    private BudgetService budgetService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private QuickExpenseService quickExpenseService;

    private UUID userId;
    private UUID walletId;
    private User user;
    private Wallet wallet;
    private WalletUser walletUser;
    private Category category;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");

        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Test Wallet");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(BigDecimal.valueOf(1000));
        wallet.setCreatedAt(Instant.now());

        walletUser = new WalletUser();
        walletUser.setUser(user);
        walletUser.setWallet(wallet);
        walletUser.setRole(WalletRole.OWNER);

        category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);
    }

    private QuickExpenseRequest request(final BigDecimal amount) {
        QuickExpenseRequest request = new QuickExpenseRequest();
        request.setAmount(amount);
        request.setDescription("Coffee");
        return request;
    }

    @Test
    void createQuickExpense_debitsWalletBalance_onCreation() {
        when(walletUserRepository
                .findFirstByUserIdAndRole(userId, WalletRole.OWNER))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(merchantDetectionService.detectCategory(any()))
                .thenReturn(category);
        when(repositoryExpense.save(any(Expense.class)))
                .thenAnswer(inv -> {
                    Expense e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    e.setCreatedAt(Instant.now());
                    return e;
                });

        quickExpenseService.createQuickExpense(
                userId, request(BigDecimal.valueOf(50))
        );

        // This is the core regression check for both exploits: the
        // fix must subtract the balance exactly once, on the same
        // locked wallet instance, before the expense is persisted.
        verify(serviceWallet, org.mockito.Mockito.times(1))
                .subtractBalance(eq(wallet), eq(BigDecimal.valueOf(50)
                        .setScale(2, java.math.RoundingMode.HALF_EVEN)));
    }

    @Test
    void createQuickExpense_usesLockedWallet_notTheStaleWalletUserReference() {
        Wallet lockedWallet = new Wallet();
        lockedWallet.setId(walletId);
        lockedWallet.setBalance(BigDecimal.valueOf(1000));
        lockedWallet.setCurrency(Currency.USD);

        when(walletUserRepository
                .findFirstByUserIdAndRole(userId, WalletRole.OWNER))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(lockedWallet));
        when(merchantDetectionService.detectCategory(any()))
                .thenReturn(category);
        when(repositoryExpense.save(any(Expense.class)))
                .thenAnswer(inv -> {
                    Expense e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    e.setCreatedAt(Instant.now());
                    return e;
                });

        quickExpenseService.createQuickExpense(
                userId, request(BigDecimal.TEN)
        );

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(repositoryExpense).save(captor.capture());

        // The persisted expense must reference the locked wallet
        // instance, not the possibly-stale one from WalletUser.
        assertEquals(lockedWallet, captor.getValue().getWallet());
        verify(serviceWallet).subtractBalance(eq(lockedWallet), any());
    }

    @Test
    void createQuickExpense_insufficientBalance_neverPersistsExpense() {
        when(walletUserRepository
                .findFirstByUserIdAndRole(userId, WalletRole.OWNER))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(merchantDetectionService.detectCategory(any()))
                .thenReturn(category);

        // Simulate ServiceWallet enforcing that balance can't go
        // negative — this is the real guard the previous
        // implementation completely bypassed.
        org.mockito.Mockito.doThrow(new IllegalArgumentException(
                        "Insufficient balance: " + wallet.getBalance()))
                .when(serviceWallet)
                .subtractBalance(eq(wallet), eq(BigDecimal.valueOf(5000)
                        .setScale(2, java.math.RoundingMode.HALF_EVEN)));

        assertThrows(IllegalArgumentException.class, () ->
                quickExpenseService.createQuickExpense(
                        userId, request(BigDecimal.valueOf(5000))
                )
        );

        // Exploit #1 regression guard: if the debit fails, no expense
        // record should ever reach the database — otherwise you'd
        // have an expense with no matching balance movement again,
        // just via a different path (a failed-but-partially-applied
        // quick expense).
        verify(repositoryExpense, never()).save(any(Expense.class));
        verify(budgetService, never())
                .evaluateBudgetsForExpenseEvent(any(), any());
    }

    @Test
    void createQuickExpense_deactivatedUser_isRejectedBeforeTouchingWallet() {
        org.mockito.Mockito.doThrow(new RuntimeException("User is inactive"))
                .when(userService).validateUserActive(userId);

        assertThrows(RuntimeException.class, () ->
                quickExpenseService.createQuickExpense(
                        userId, request(BigDecimal.TEN)
                )
        );

        verify(repositoryWallet, never()).findByIdForUpdate(any());
        verify(serviceWallet, never()).subtractBalance(any(), any());
    }

    @Test
    void createQuickExpense_missingWallet_throwsResourceNotFound() {
        when(walletUserRepository
                .findFirstByUserIdAndRole(userId, WalletRole.OWNER))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                quickExpenseService.createQuickExpense(
                        userId, request(BigDecimal.TEN)
                )
        );

        verify(serviceWallet, never()).subtractBalance(any(), any());
        verify(repositoryExpense, never()).save(any());
    }

    @Test
    void createQuickExpense_roundsAmountToTwoDecimals_beforeDebitAndSave() {
        when(walletUserRepository
                .findFirstByUserIdAndRole(userId, WalletRole.OWNER))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(merchantDetectionService.detectCategory(any()))
                .thenReturn(category);
        when(repositoryExpense.save(any(Expense.class)))
                .thenAnswer(inv -> {
                    Expense e = inv.getArgument(0);
                    e.setId(UUID.randomUUID());
                    e.setCreatedAt(Instant.now());
                    return e;
                });

        // Three decimals in, HALF_EVEN rounding to two decimals out —
        // matches ServiceExpense's behavior so both entry points
        // agree on wallet precision.
        ExpenseResponse response = quickExpenseService.createQuickExpense(
                userId, request(new BigDecimal("12.346"))
        );

        BigDecimal expected = new BigDecimal("12.35").setScale(
                2, java.math.RoundingMode.HALF_EVEN);

        assertEquals(0, expected.compareTo(response.getAmount()));
        verify(serviceWallet).subtractBalance(eq(wallet), eq(expected));
    }
}
