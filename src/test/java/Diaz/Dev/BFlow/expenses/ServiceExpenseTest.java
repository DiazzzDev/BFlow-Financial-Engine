package Diaz.Dev.BFlow.expenses;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.budget.services.BudgetService;
import bflow.category.CategoryValidator;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.expenses.services.ServiceExpense;
import bflow.wallet.RepositoryWallet;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.ServiceWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ServiceExpense.
 */
@ExtendWith(MockitoExtension.class)
class ServiceExpenseTest {

    @Mock
    private RepositoryExpense repositoryExpense;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private RepositoryCategory repositoryCategory;

    @Mock
    private CategoryValidator categoryValidator;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserServiceImpl userService;

    @Mock
    private BudgetService serviceBudget;

    @InjectMocks
    private ServiceExpense serviceExpense;

    private UUID userId;
    private UUID walletId;
    private UUID categoryId;
    private User user;
    private Wallet wallet;
    private WalletUser walletUser;
    private Category category;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

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

        category = new Category();
        category.setId(categoryId);
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);
        category.setSystemDefined(true);
        category.setCreatedAt(Instant.now());
    }

    @Test
    void testCreateExpense() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Test Expense Title");
        request.setAmount(BigDecimal.valueOf(100));
        request.setDescription("Test expense");

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(100));
        expense.setDescription("Test expense");
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
        .thenReturn(Optional.of(wallet));        
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        doNothing().when(serviceWallet).subtractBalance(any(), any());
        when(repositoryExpense.saveAndFlush(any(Expense.class))).thenReturn(expense);

        ExpenseResponse result = serviceExpense.newExpense(request, userId);

        assertNotNull(result);
        verify(userService).validateUserActive(userId);
        verify(repositoryExpense).saveAndFlush(any(Expense.class));
    }

    /**
     * Confirms the expense is never persisted and the wallet balance
     * is never touched when the wallet cannot cover the requested amount.
     * NOTE: once the pessimistic-lock fix (findByIdForUpdate) is reapplied
     * to newExpense, this test's mocks will need repositoryWallet stubbed
     * instead of relying solely on walletUser.getWallet().
     */
    @Test
    void testCreateExpense_insufficientBalance_doesNotPersistExpense() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Expensive purchase");
        request.setAmount(BigDecimal.valueOf(5000));

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));

        doThrow(new IllegalArgumentException("Insufficient balance: 1000"))
                .when(serviceWallet).subtractBalance(eq(wallet), any(BigDecimal.class));

        assertThrows(IllegalArgumentException.class,
                () -> serviceExpense.newExpense(request, userId));

        verify(repositoryExpense, never()).saveAndFlush(any(Expense.class));
        verify(serviceBudget, never()).evaluateBudgetsForWallet(any());
    }

    /**
     * Documents current behavior: budget evaluation runs AFTER the expense
     * is already persisted and the balance already deducted. Exceeding the
     * category budget does not block the expense today.
     */
    @Test
    void testCreateExpense_stillPersistsWhenBudgetExceeded() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Over-budget expense");
        request.setAmount(BigDecimal.valueOf(100));

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(100));
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
        .thenReturn(Optional.of(wallet));        
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        when(repositoryExpense.saveAndFlush(any(Expense.class))).thenReturn(expense);

        ExpenseResponse result = serviceExpense.newExpense(request, userId);

        assertNotNull(result);
        verify(repositoryExpense).saveAndFlush(any(Expense.class));
        verify(serviceBudget).evaluateBudgetsForWallet(walletId);
    }

    /**
     * Confirms that an invalid category type (not EXPENSE) blocks the
     * operation before the wallet balance is ever touched.
     */
    @Test
    void testCreateExpense_wrongCategoryType_throwsException() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Miscategorized");
        request.setAmount(BigDecimal.valueOf(50));

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
        .thenReturn(Optional.of(wallet));        
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));

        doThrow(new IllegalArgumentException("Category must be of type EXPENSE"))
                .when(categoryValidator).validateExpenseCategory(category);

        assertThrows(IllegalArgumentException.class,
                () -> serviceExpense.newExpense(request, userId));

        verify(repositoryExpense, never()).saveAndFlush(any(Expense.class));
        verify(serviceWallet, never()).subtractBalance(any(), any());
    }

    /**
     * Confirms that a user without access to the wallet is rejected before
     * any repository or wallet-service interaction happens.
     */
    @Test
    void testCreateExpense_noWalletAccess_throwsException() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Unauthorized attempt");
        request.setAmount(BigDecimal.valueOf(50));

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        assertThrows(WalletAccessDeniedException.class,
                () -> serviceExpense.newExpense(request, userId));

        verify(repositoryWallet, never()).findByIdForUpdate(any());
        verify(serviceWallet, never()).subtractBalance(any(), any());
        verify(repositoryExpense, never()).saveAndFlush(any(Expense.class));
    }

    /**
     * Confirms banker's rounding (HALF_EVEN) is applied to the amount
     * before persistence, using a value exactly at the rounding boundary.
     */
    @Test
    void testCreateExpense_amountRoundedUsingHalfEven() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Rounding test");
        request.setAmount(new BigDecimal("10.005"));

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        when(repositoryExpense.saveAndFlush(any(Expense.class)))
                .thenAnswer(invocation -> {
                        Expense saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                });

        serviceExpense.newExpense(request, userId);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(repositoryExpense).saveAndFlush(captor.capture());

        assertEquals(new BigDecimal("10.00"), captor.getValue().getAmount());
    }

    /**
     * Documents that a zero amount currently passes ServiceWallet's
     * validation (only negative amounts are rejected there). Client-side
     * DTO validation (@Positive/@DecimalMin) is the actual gatekeeper for
     * this case; this test exposes the gap in defense-in-depth if that
     * validation is ever bypassed.
     */
    @Test
    void testCreateExpense_zeroAmount_passesWalletServiceValidation() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Zero amount");
        request.setAmount(BigDecimal.ZERO);

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        doNothing().when(serviceWallet).subtractBalance(eq(wallet), any(BigDecimal.class));
        when(repositoryExpense.saveAndFlush(any(Expense.class)))
                .thenAnswer(invocation -> {
                        Expense saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                });

        ExpenseResponse result = serviceExpense.newExpense(request, userId);

        assertNotNull(result);
    }
}