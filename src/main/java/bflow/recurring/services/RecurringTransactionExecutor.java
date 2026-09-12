package bflow.recurring.services;

import bflow.auth.enums.SupportedLanguage;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.services.ServiceExpense;
import bflow.income.DTO.IncomeRequest;
import bflow.income.ServiceIncome;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Executes individual recurring transactions in isolated transactions
 * so that a single failure never rolls back or blocks the rest of the
 * batch, and records/notifies failures without corrupting execution
 * of the following recurring transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTransactionExecutor {

    /**
     * Consecutive failures after which the recurring
     * transaction is auto-paused.
     */
    private static final int MAX_FAILED_ATTEMPTS = 3;

    /** Max length stored for the failure reason (matches column size). */
    private static final int MAX_REASON_LENGTH = 150;

    /** Repository for recurring transaction persistence. */
    private final RepositoryRecurringTransaction repository;

    /**
     * Service used to create the expense
     * when the recurring type is EXPENSE.
     */
    private final ServiceExpense serviceExpense;

    /** Service used to create the income when the recurring type is INCOME. */
    private final ServiceIncome serviceIncome;

    /**
     * Executes a single recurring transaction in its own transaction,
     * independent of the batch. Re-fetches the entity by ID so it is
     * managed within this transaction (the instance from the batch
     * loop is detached once the outer query completes).
     *
     * @param recurringId the recurring transaction ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeSingle(final UUID recurringId) {
        RecurringTransaction recurring = repository.findById(recurringId)
                .orElseThrow(() -> new IllegalStateException(
                        "Recurring transaction not found: " + recurringId));

        if (recurring.getType() == RecurringType.EXPENSE) {
            createExpense(recurring);
        } else {
            createIncome(recurring);
        }

        updateNextExecution(recurring);
        recurring.setFailedAttempts(0);
        recurring.setLastFailureReason(null);
    }

    /**
     * Records a failed execution attempt in its own isolated
     * transaction and returns the data needed to send a notification
     * email (extracted here, since lazy associations become
     * unusable once this transaction/persistence context closes).
     *
     * @param recurringId the recurring transaction ID
     * @param error the exception that caused the failure
     * @return notification data, or {@code null} if the recurring
     *         transaction no longer exists
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureNotification recordFailure(
            final UUID recurringId,
            final Exception error
    ) {
        RecurringTransaction recurring = repository.findById(recurringId)
                .orElse(null);

        if (recurring == null) {
            log.warn("Recurring transaction {} disappeared before "
                    + "failure could be recorded", recurringId);
            return null;
        }

        int attempts = recurring.getFailedAttempts() + 1;
        String reason = truncate(error.getMessage());
        boolean deactivated = attempts >= MAX_FAILED_ATTEMPTS;

        recurring.setFailedAttempts(attempts);
        recurring.setLastFailureReason(reason);
        recurring.setLastFailureAt(Instant.now());

        if (deactivated) {
            recurring.setActive(false);
        }

        String email = recurring.getUser().getEmail();
        String userName = recurring.getUser().getName();
        SupportedLanguage language = recurring.getUser().getLanguage();

        return new FailureNotification(
                email, userName, recurring.getTitle(),
                recurring.getAmount(), attempts, deactivated, reason,
                language
        );
    }

    private String truncate(final String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > MAX_REASON_LENGTH
                ? message.substring(0, MAX_REASON_LENGTH)
                : message;
    }

    private void createExpense(final RecurringTransaction recurring) {
        ExpenseRequest request = new ExpenseRequest();
        request.setTitle(recurring.getTitle());
        request.setDescription(recurring.getDescription());
        request.setAmount(recurring.getAmount());
        request.setDate(LocalDate.now());
        request.setWalletId(recurring.getWallet().getId());
        request.setCategoryId(recurring.getCategory().getId());
        request.setSource("recurring");
        request.setRecurring(true);

        serviceExpense.newExpense(request, recurring.getUser().getId());
    }

    private void createIncome(final RecurringTransaction recurring) {
        IncomeRequest request = new IncomeRequest();
        request.setTitle(recurring.getTitle());
        request.setDescription(recurring.getDescription());
        request.setAmount(recurring.getAmount());
        request.setDate(LocalDate.now());
        request.setWalletId(recurring.getWallet().getId());
        request.setCategoryId(recurring.getCategory().getId());
        request.setSource("recurring");
        request.setRecurring(true);

        serviceIncome.newIncome(request, recurring.getUser().getId());
    }

    private void updateNextExecution(final RecurringTransaction recurring) {
        LocalDate next = recurring.getNextExecutionDate();
        LocalDate nextDate = next;

        switch (recurring.getFrequency()) {
            case DAILY:
                nextDate = next.plusDays(recurring.getIntervalValue());
                break;
            case WEEKLY:
                nextDate = next.plusWeeks(recurring.getIntervalValue());
                break;
            case MONTHLY:
                nextDate = next.plusMonths(recurring.getIntervalValue());
                break;
            default:
                break;
        }

        recurring.setNextExecutionDate(nextDate);
    }

    /**
     * Data needed to compose the failure email, extracted while the
     * recurring transaction's persistence context is still open.
     *
     * @param email recipient email address
     * @param userName recipient display name
     * @param transactionTitle title of the recurring transaction
     * @param amount the transaction amount
     * @param attempts number of consecutive failed attempts so far
     * @param deactivated whether the recurring transaction was
     *        auto-deactivated after reaching the failure threshold
     * @param reason short description of why the execution failed
     */
    public record FailureNotification(
            String email,
            String userName,
            String transactionTitle,
            BigDecimal amount,
            int attempts,
            boolean deactivated,
            String reason,
            SupportedLanguage language
    ) { }
}
