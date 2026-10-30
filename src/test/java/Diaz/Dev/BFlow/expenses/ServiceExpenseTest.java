package Diaz.Dev.BFlow.expenses;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.budget.services.BudgetService;
import bflow.category.CategoryValidator;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.exception.FileAccessDeniedException;
import bflow.common.financial.InvalidTransactionDateException;
import bflow.common.financial.TransactionDatePolicy;
import bflow.common.i18n.MessageService;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.expenses.services.ServiceExpense;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringLinkService;
import bflow.storage.entity.StoredFile;
import bflow.storage.enums.FileStatus;
import bflow.storage.repository.RepositoryStoredFile;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;
import bflow.wallet.service.WalletLockService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private WalletLockService walletLockService;

    @Mock
    private RepositoryCategory repositoryCategory;

    @Mock
    private CategoryValidator categoryValidator;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserService userService;

    @Mock
    private BudgetService serviceBudget;

    @Mock
    private RecurringLinkService recurringLinkService;

    @Mock
    private MessageService messageService;

    @Mock
    private TransactionDatePolicy transactionDatePolicy;

    @InjectMocks
    private ServiceExpense serviceExpense;

    @Mock
    private RepositoryStoredFile repositoryStoredFile;

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
        LocalDate transactionDate = LocalDate.of(2026, 10, 5);
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Test Expense Title");
        request.setAmount(BigDecimal.valueOf(100));
        request.setDescription("Test expense");
        request.setDate(transactionDate);

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(100));
        expense.setDescription("Test expense");
        expense.setDate(transactionDate);
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

        assertEquals(transactionDate, result.getDate());
        verify(transactionDatePolicy).validateRequired(transactionDate);
        verify(userService).validateUserActive(userId);
        verify(repositoryExpense).saveAndFlush(any(Expense.class));
    }

    @Test
    void invalidHistoricalDateDoesNotPersistExpenseOrChangeBalance() {
        ExpenseRequest request = new ExpenseRequest();
        request.setDate(LocalDate.of(1500, 1, 1));
        org.mockito.Mockito.doThrow(new InvalidTransactionDateException(
                "date must be within the supported range"))
                .when(transactionDatePolicy).validateRequired(request.getDate());

        assertThrows(InvalidTransactionDateException.class,
                () -> serviceExpense.newExpense(request, userId));

        verify(repositoryExpense, never()).saveAndFlush(any());
        verify(serviceWallet, never()).subtractBalance(any(), any());
        verify(serviceBudget, never()).evaluateBudgetsForExpenseEvent(
                any(), any());
    }

    @Test
    void oneDayBeforeHistoricalLimitCannotCreateExpense() {
        LocalDate invalidDate = LocalDate.of(1976, 10, 4);
        ExpenseRequest request = new ExpenseRequest();
        request.setDate(invalidDate);
        doThrow(new InvalidTransactionDateException(
                "date must be within the supported range"))
                .when(transactionDatePolicy).validateRequired(invalidDate);

        assertThrows(InvalidTransactionDateException.class,
                () -> serviceExpense.newExpense(request, userId));

        verify(repositoryExpense, never()).saveAndFlush(any());
        verify(serviceWallet, never()).subtractBalance(any(), any());
    }

    @Test
    void futureDateDoesNotUpdateExpenseOrChangeBalance() {
        ExpenseRequest request = new ExpenseRequest();
        request.setDate(LocalDate.of(9999, 12, 31));
        org.mockito.Mockito.doThrow(new InvalidTransactionDateException(
                "date must be within the supported range"))
                .when(transactionDatePolicy).validateRequired(request.getDate());

        assertThrows(InvalidTransactionDateException.class,
                () -> serviceExpense.updateExpense(
                        UUID.randomUUID(), request, userId));

        verify(repositoryExpense, never()).save(any());
        verify(serviceWallet, never()).adjustBalanceForExpenseUpdate(
                any(), any(), any());
        verify(serviceWallet, never()).subtractBalance(any(), any());
    }

    @Test
    void testCreateExpense_pendingDeletionUserIsRejectedBeforeWalletMutation() {
        user.setStatus(UserStatus.PENDING_DELETION);
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        org.mockito.Mockito.doThrow(new IllegalStateException("inactive"))
                .when(userService).validateUserActive(userId);

        assertThrows(IllegalStateException.class,
                () -> serviceExpense.newExpense(request, userId));

        verify(repositoryWalletUser, never())
                .findByWalletIdAndUserId(any(), any());
        verify(repositoryWallet, never()).findByIdForUpdate(any());
        verify(serviceWallet, never()).subtractBalance(any(), any());
        verify(repositoryExpense, never()).saveAndFlush(any());
        verify(serviceBudget, never()).evaluateBudgetsForExpenseEvent(
                any(), any());
    }

    @Test
    void testCreateExpense_withoutOptionalCategoryPersistsAndEvaluatesWalletBudget() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setTitle("Uncategorized expense");
        request.setAmount(new BigDecimal("12.34"));
        request.setDate(LocalDate.of(2026, 10, 5));

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryExpense.saveAndFlush(any(Expense.class)))
                .thenAnswer(invocation -> {
                    Expense saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        ExpenseResponse response = serviceExpense.newExpense(request, userId);

        ArgumentCaptor<Expense> savedExpense =
                ArgumentCaptor.forClass(Expense.class);
        verify(repositoryExpense).saveAndFlush(savedExpense.capture());
        assertNotNull(response);
        assertEquals("Uncategorized expense", savedExpense.getValue().getTitle());
        assertNull(savedExpense.getValue().getCategory());
        assertNull(savedExpense.getValue().getDescription());
        verify(repositoryCategory, never()).findById(any());
        verify(categoryValidator, never()).validateExpenseCategory(any());
        verify(serviceBudget).evaluateBudgetsForExpenseEvent(walletId, null);
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
        verify(serviceBudget, never()).evaluateBudgetsForExpenseEvent(any(), any());
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
        verify(serviceBudget).evaluateBudgetsForExpenseEvent(eq(walletId), any());
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

    @Test
    void testUpdateExpense_sameWalletAdjustsOnlyTheDifference() {
        UUID expenseId = UUID.randomUUID();
        Expense existing = new Expense();
        existing.setId(expenseId);
        existing.setWallet(wallet);
        existing.setCategory(category);
        existing.setContributor(user);
        existing.setAmount(new BigDecimal("25.00"));
        existing.setTitle("Old title");
        existing.setDate(LocalDate.of(2026, 10, 1));
        existing.setCreatedAt(Instant.now());

        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Updated title");
        request.setAmount(new BigDecimal("30.00"));
        request.setDate(LocalDate.of(2026, 10, 5));
        request.setRecurring(true);
        request.setRecurrencePattern("MONTHLY");
        UUID recurringId = UUID.randomUUID();

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryExpense.findById(expenseId)).thenReturn(Optional.of(existing));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(walletLockService.lockWallets(walletId, walletId))
                .thenReturn(new bflow.wallet.DTO.WalletPair(wallet, wallet));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        when(recurringLinkService.syncOnUpdate(any())).thenReturn(recurringId);
        when(repositoryExpense.save(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse response =
                serviceExpense.updateExpense(expenseId, request, userId);

        assertNotNull(response);
        assertEquals("Updated title", existing.getTitle());
        assertEquals(new BigDecimal("30.00"), existing.getAmount());
        assertEquals(recurringId, existing.getRecurringTransactionId());
        assertTrue(existing.getRecurring());
        verify(serviceWallet).adjustBalanceForExpenseUpdate(
                wallet, new BigDecimal("25.00"), new BigDecimal("30.00"));
        verify(serviceWallet, never()).reverseTransactionImpact(any(), any());
        verify(serviceWallet, never()).subtractBalance(any(), any());
        verify(serviceBudget).evaluateBudgetsForExpenseEvent(walletId, categoryId);
        verify(repositoryExpense).save(existing);
    }

    @Test
    void testDeleteExpense_uncategorizedRestoresBalanceAndReevaluatesWalletBudget() {
        UUID expenseId = UUID.randomUUID();
        Expense existing = new Expense();
        existing.setId(expenseId);
        existing.setWallet(wallet);
        existing.setContributor(user);
        existing.setAmount(new BigDecimal("12.34"));

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryExpense.findById(expenseId)).thenReturn(Optional.of(existing));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        serviceExpense.deleteExpense(expenseId, userId);

        verify(serviceWallet).addBalance(wallet, new BigDecimal("12.34"));
        verify(repositoryExpense).delete(existing);
        verify(serviceBudget).evaluateBudgetsForExpenseEvent(walletId, null);
    }

    @Test
    void testCreateExpense_recurring_createsRecurringLinkAndStoresId() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("  Netflix subscription  ");
        request.setDescription("  Family plan  ");
        request.setAmount(new BigDecimal("15.25"));
        request.setDate(LocalDate.of(2026, 8, 17));
        request.setRecurring(true);
        request.setRecurrencePattern("MONTHLY");

        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setId(UUID.randomUUID());

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(recurringLinkService.linkRecurring(
                any(RecurringLinkService.RecurringCreateRequest.class)))
                .thenReturn(recurring);
        when(repositoryExpense.saveAndFlush(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse result = serviceExpense.newExpense(request, userId);

        assertNotNull(result);

        ArgumentCaptor<RecurringLinkService.RecurringCreateRequest> captor =
                ArgumentCaptor.forClass(
                        RecurringLinkService.RecurringCreateRequest.class);
        verify(recurringLinkService).linkRecurring(captor.capture());

        RecurringLinkService.RecurringCreateRequest captured = captor.getValue();
        assertEquals(RecurringType.EXPENSE, captured.type());
        assertEquals("MONTHLY", captured.rawFrequency());
        assertEquals(wallet, captured.wallet());
        assertEquals(category, captured.category());
        assertEquals(user, captured.user());
        assertEquals("Netflix subscription", captured.title());
        assertEquals("  Family plan  ", captured.description());
        assertEquals(new BigDecimal("15.25"), captured.amount());
        assertEquals(LocalDate.of(2026, 8, 17), captured.startDate());
        ArgumentCaptor<Expense> saved =
                ArgumentCaptor.forClass(Expense.class);
        verify(repositoryExpense).saveAndFlush(saved.capture());
        assertEquals("Netflix subscription", saved.getValue().getTitle());
        assertEquals("  Family plan  ", saved.getValue().getDescription());
        assertEquals(new BigDecimal("15.25"), saved.getValue().getAmount());
        assertEquals(LocalDate.of(2026, 8, 17), saved.getValue().getDate());
        assertEquals(wallet, saved.getValue().getWallet());
        assertEquals(category, saved.getValue().getCategory());
        assertEquals(user, saved.getValue().getContributor());
        assertTrue(saved.getValue().getRecurring());
        assertEquals("MONTHLY", saved.getValue().getRecurrencePattern());
        assertEquals(recurring.getId(),
                saved.getValue().getRecurringTransactionId());
    }

    @Test
    void testCreateExpense_recurringFalse_neverCallsRecurringLinkService() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Regular purchase");
        request.setAmount(BigDecimal.valueOf(50));

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(50));
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(repositoryExpense.saveAndFlush(any(Expense.class)))
                .thenReturn(expense);

        serviceExpense.newExpense(request, userId);

        verify(recurringLinkService, never()).linkRecurring(
                any(RecurringLinkService.RecurringCreateRequest.class));
    }

    /**
     * Simulates RecurringTransactionExecutor firing a due recurring
     * expense (source="recurring", recurring=true). Must NOT create
     * another RecurringTransaction, or every scheduled execution would
     * spawn a new template indefinitely.
     */
    @Test
    void testCreateExpense_autoGeneratedByExecutor_neverCreatesAnotherLink() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Netflix subscription");
        request.setAmount(BigDecimal.valueOf(15));
        request.setRecurring(true);
        request.setRecurrencePattern("MONTHLY");
        request.setSource("recurring");
        request.setDate(LocalDate.of(2026, 10, 5));

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(15));
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(repositoryExpense.saveAndFlush(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        serviceExpense.newExpense(request, userId);

        verify(recurringLinkService, never()).linkRecurring(
                any(RecurringLinkService.RecurringCreateRequest.class));
        ArgumentCaptor<Expense> saved =
                ArgumentCaptor.forClass(Expense.class);
        verify(repositoryExpense).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().getAutoGenerated());
        assertTrue(saved.getValue().getRecurring());
        assertEquals("recurring", saved.getValue().getSource());
        assertEquals("MONTHLY", saved.getValue().getRecurrencePattern());
        assertEquals(LocalDate.of(2026, 10, 5), saved.getValue().getDate());
    }

    @Test
    void testCreateExpense_withUploadedReceipt_attachesReceiptFileId() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Grocery run");
        request.setAmount(BigDecimal.valueOf(45));
        UUID receiptId = UUID.randomUUID();
        request.setReceiptFileId(receiptId);

        StoredFile receipt = new StoredFile();
        receipt.setId(receiptId);
        receipt.setStatus(FileStatus.UPLOADED);
        receipt.setContentType("image/jpeg");

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(45));
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(repositoryStoredFile.findByIdAndUserId(receiptId, userId))
                .thenReturn(Optional.of(receipt));
        when(repositoryExpense.saveAndFlush(any(Expense.class)))
                .thenReturn(expense);

        ExpenseResponse result = serviceExpense.newExpense(request, userId);

        assertNotNull(result);
        verify(repositoryStoredFile).findByIdAndUserId(receiptId, userId);
    }

    @Test
    void testCreateExpense_receiptNotUploaded_throwsIllegalStateException() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Grocery run");
        request.setAmount(BigDecimal.valueOf(45));

        UUID receiptId = UUID.randomUUID();
        request.setReceiptFileId(receiptId);

        StoredFile receipt = new StoredFile();
        receipt.setId(receiptId);
        receipt.setStatus(FileStatus.PENDING);

        doNothing().when(userService).validateUserActive(userId);

        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));

        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        when(repositoryUser.findById(userId))
                .thenReturn(Optional.of(user));

        when(repositoryStoredFile.findByIdAndUserId(receiptId, userId))
                .thenReturn(Optional.of(receipt));

        assertThrows(
                IllegalStateException.class,
                () -> serviceExpense.newExpense(request, userId)
        );

        verify(repositoryStoredFile)
                .findByIdAndUserId(receiptId, userId);

        verify(repositoryCategory, never())
                .findById(any());

        verify(categoryValidator, never())
                .validateExpenseCategory(any());

        verify(serviceWallet, never())
                .subtractBalance(any(), any());

        verify(repositoryExpense, never())
                .saveAndFlush(any());
    }

    @Test
    void testCreateExpense_receiptNotOwnedByUser_throwsFileAccessDenied() {
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Grocery run");
        request.setAmount(BigDecimal.valueOf(45));

        UUID receiptId = UUID.randomUUID();
        request.setReceiptFileId(receiptId);

        doNothing().when(userService).validateUserActive(userId);

        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));

        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        when(repositoryUser.findById(userId))
                .thenReturn(Optional.of(user));

        when(repositoryStoredFile.findByIdAndUserId(receiptId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                FileAccessDeniedException.class,
                () -> serviceExpense.newExpense(request, userId)
        );

        verify(repositoryStoredFile)
                .findByIdAndUserId(receiptId, userId);

        verify(repositoryCategory, never())
                .findById(any());

        verify(categoryValidator, never())
                .validateExpenseCategory(any());

        verify(serviceWallet, never())
                .subtractBalance(any(), any());

        verify(repositoryExpense, never())
                .saveAndFlush(any());
    }
}
