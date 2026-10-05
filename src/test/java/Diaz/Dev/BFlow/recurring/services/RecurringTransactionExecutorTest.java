package Diaz.Dev.BFlow.recurring.services;

import bflow.auth.entities.User;
import bflow.category.entity.Category;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.services.ServiceExpense;
import bflow.income.DTO.IncomeRequest;
import bflow.income.DTO.IncomeResponse;
import bflow.income.ServiceIncome;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringTransactionExecutor;
import bflow.wallet.entities.Wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RecurringTransactionExecutor: the auto-deactivation
 * threshold, failure-message truncation (must respect the DB column
 * length or persistence itself would throw), success-path recovery
 * of a previously-failing recurring transaction, and next-execution
 * date math per frequency.
 */
@ExtendWith(MockitoExtension.class)
class RecurringTransactionExecutorTest {

    @Mock
    private RepositoryRecurringTransaction repository;

    @Mock
    private ServiceExpense serviceExpense;

    @Mock
    private ServiceIncome serviceIncome;

    @InjectMocks
    private RecurringTransactionExecutor executor;

    private UUID recurringId;
    private RecurringTransaction recurring;
    private User user;
    private Wallet wallet;
    private Category category;

    @BeforeEach
    void setUp() {
        recurringId = UUID.randomUUID();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setName("Test User");

        wallet = new Wallet();
        wallet.setId(UUID.randomUUID());

        category = new Category();
        category.setId(UUID.randomUUID());

        recurring = new RecurringTransaction();
        recurring.setId(recurringId);
        recurring.setType(RecurringType.EXPENSE);
        recurring.setTitle("Netflix");
        recurring.setDescription("Subscription");
        recurring.setAmount(BigDecimal.valueOf(15));
        recurring.setFrequency(RecurringFrequency.MONTHLY);
        recurring.setIntervalValue(1);
        recurring.setNextExecutionDate(LocalDate.of(2026, 8, 17));
        recurring.setStartDate(LocalDate.of(2026, 7, 17));
        recurring.setWallet(wallet);
        recurring.setCategory(category);
        recurring.setUser(user);
        recurring.setActive(true);
        recurring.setFailedAttempts(0);
    }

    // ---- executeSingle ----

    @Test
    void executeSingle_notFound_throwsIllegalStateException() {
        when(repository.findById(recurringId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> executor.executeSingle(recurringId));

        verify(serviceExpense, never()).newExpense(any(), any());
        verify(serviceIncome, never()).newIncome(any(), any());
    }

    @Test
    void executeSingle_expenseType_callsServiceExpenseWithRecurringSource() {
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), any()))
                .thenReturn(new ExpenseResponse());

        executor.executeSingle(recurringId);

        ArgumentCaptor<ExpenseRequest> captor =
                ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(serviceExpense).newExpense(captor.capture(), any());

        ExpenseRequest sent = captor.getValue();
        assertEquals("recurring", sent.getSource());
        assertTrue(sent.getRecurring());
        assertEquals(recurring.getAmount(), sent.getAmount());
        assertEquals(wallet.getId(), sent.getWalletId());
        verify(serviceIncome, never()).newIncome(any(), any());
    }

    @Test
    void executeSingle_incomeType_callsServiceIncomeNotExpense() {
        recurring.setType(RecurringType.INCOME);
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceIncome.newIncome(any(IncomeRequest.class), any()))
                .thenReturn(new IncomeResponse());

        executor.executeSingle(recurringId);

        verify(serviceIncome).newIncome(any(IncomeRequest.class), any());
        verify(serviceExpense, never()).newExpense(any(), any());
    }

    @Test
    void executeSingle_success_resetsFailedAttemptsAndReason() {
        // Recovery path: a recurring transaction that previously
        // failed twice must clear its failure state on a successful
        // execution, or it would stay one bad month away from
        // auto-deactivation forever even after recovering.
        recurring.setFailedAttempts(2);
        recurring.setLastFailureReason("Insufficient balance");

        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), any()))
                .thenReturn(new ExpenseResponse());

        executor.executeSingle(recurringId);

        assertEquals(0, recurring.getFailedAttempts());
        assertNull(recurring.getLastFailureReason());
    }

    @Test
    void executeSingle_monthlyFrequency_advancesNextExecutionByOneMonth() {
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), any()))
                .thenReturn(new ExpenseResponse());

        executor.executeSingle(recurringId);

        assertEquals(LocalDate.of(2026, 9, 17),
                recurring.getNextExecutionDate());
    }

    @Test
    void executeSingle_weeklyFrequency_advancesNextExecutionByInterval() {
        recurring.setFrequency(RecurringFrequency.WEEKLY);
        recurring.setIntervalValue(2);
        recurring.setNextExecutionDate(LocalDate.of(2026, 8, 3));

        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), any()))
                .thenReturn(new ExpenseResponse());

        executor.executeSingle(recurringId);

        // 2-week interval, not the default 1.
        assertEquals(LocalDate.of(2026, 8, 17),
                recurring.getNextExecutionDate());
    }

    @Test
    void executeSingle_dailyFrequency_advancesNextExecutionByOneDay() {
        recurring.setFrequency(RecurringFrequency.DAILY);
        recurring.setNextExecutionDate(LocalDate.of(2026, 8, 17));

        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), any()))
                .thenReturn(new ExpenseResponse());

        executor.executeSingle(recurringId);

        assertEquals(LocalDate.of(2026, 8, 18),
                recurring.getNextExecutionDate());
    }

    @Test
    void executeSingle_yearlyFrequency_advancesNextExecutionByInterval() {
        recurring.setFrequency(RecurringFrequency.YEARLY);
        recurring.setIntervalValue(2);
        recurring.setNextExecutionDate(LocalDate.of(2026, 8, 17));

        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), any()))
                .thenReturn(new ExpenseResponse());

        executor.executeSingle(recurringId);

        assertEquals(LocalDate.of(2028, 8, 17),
                recurring.getNextExecutionDate());
    }

    @Test
    void executeSingle_monthEndDate_clampsInsteadOfThrowing() {
        // Jan 31 + 1 month must clamp to Feb 28/29, not throw — this
        // is java.time's built-in behavior, but it's exactly the kind
        // of date-math edge case worth pinning down explicitly for a
        // billing-date calculation.
        recurring.setNextExecutionDate(LocalDate.of(2026, 1, 31));

        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), any()))
                .thenReturn(new ExpenseResponse());

        executor.executeSingle(recurringId);

        assertEquals(LocalDate.of(2026, 2, 28),
                recurring.getNextExecutionDate());
    }

    // ---- recordFailure ----

    @Test
    void recordFailure_belowThreshold_incrementsButStaysActive() {
        recurring.setFailedAttempts(1);
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));

        RecurringTransactionExecutor.FailureNotification result =
                executor.recordFailure(recurringId,
                        new RuntimeException("Insufficient balance"));

        assertEquals(2, recurring.getFailedAttempts());
        assertTrue(recurring.getActive());
        assertFalse(result.deactivated());
        assertEquals(2, result.attempts());
    }

    @Test
    void recordFailure_reachesThreshold_autoDeactivates() {
        // Third consecutive failure: must flip active=false so the
        // scheduler stops retrying a recurring charge that is
        // structurally broken (e.g. wallet deleted, category removed).
        recurring.setFailedAttempts(2);
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));

        RecurringTransactionExecutor.FailureNotification result =
                executor.recordFailure(recurringId,
                        new RuntimeException("Insufficient balance"));

        assertEquals(3, recurring.getFailedAttempts());
        assertFalse(recurring.getActive());
        assertTrue(result.deactivated());
    }

    @Test
    void recordFailure_recurringDeletedMidBatch_returnsNullWithoutThrowing() {
        // The batch job re-fetches by ID inside its own transaction;
        // if the recurring transaction was deleted between the batch
        // query and this isolated execution, this must degrade
        // gracefully, not throw and abort the rest of the batch.
        when(repository.findById(recurringId)).thenReturn(Optional.empty());

        RecurringTransactionExecutor.FailureNotification result =
                executor.recordFailure(recurringId,
                        new RuntimeException("Insufficient balance"));

        assertNull(result);
    }

    @Test
    void recordFailure_nullExceptionMessage_storesUnknownErrorPlaceholder() {
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));

        executor.recordFailure(recurringId, new RuntimeException());

        assertEquals("Unknown error", recurring.getLastFailureReason());
    }

    @Test
    void recordFailure_messageExceedsColumnLength_isTruncatedTo150Chars() {
        // lastFailureReason is @Column(length = 150). An untruncated
        // message here wouldn't throw here (that's the point of
        // testing it) but WOULD blow up at persist() with a
        // DataException if this weren't truncated first.
        String longMessage = "x".repeat(300);
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));

        executor.recordFailure(recurringId, new RuntimeException(longMessage));

        assertEquals(150, recurring.getLastFailureReason().length());
    }

    @Test
    void recordFailure_messageExactlyAtLimit_isNotTruncated() {
        String exactMessage = "x".repeat(150);
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));

        executor.recordFailure(recurringId, new RuntimeException(exactMessage));

        assertEquals(150, recurring.getLastFailureReason().length());
        assertEquals(exactMessage, recurring.getLastFailureReason());
    }

    @Test
    void recordFailure_notificationCarriesCorrectRecipientData() {
        when(repository.findById(recurringId))
                .thenReturn(Optional.of(recurring));

        RecurringTransactionExecutor.FailureNotification result =
                executor.recordFailure(recurringId,
                        new RuntimeException("Card declined"));

        assertEquals("user@example.com", result.email());
        assertEquals("Test User", result.userName());
        assertEquals("Netflix", result.transactionTitle());
        assertEquals(BigDecimal.valueOf(15), result.amount());
    }
}
