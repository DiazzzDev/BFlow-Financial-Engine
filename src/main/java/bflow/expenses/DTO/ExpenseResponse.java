package bflow.expenses.DTO;

import bflow.category.DTO.CategoryResponse;
import bflow.common.financial.TransactionResponseAccessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable API response for an expense. */
public record ExpenseResponse(
        String id,
        String title,
        String description,
        BigDecimal amount,
        LocalDate date,
        CategoryResponse category,
        Boolean recurring,
        String walletId,
        String walletName,
        String contributorId,
        String contributorName,
        String source,
        Instant createdAt,
        Integer editCount,
        Integer categorizationChanges,
        Double confidenceScore,
        String recurrencePattern,
        String recurringTransactionId,
        String receiptFileId
) implements TransactionResponseAccessors {
    /** Empty response for interaction tests that only need a stub value. */
    public static ExpenseResponse empty() {
        return new ExpenseResponse(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    /** Creates a minimal response for stubbing a persisted expense. */
    public static ExpenseResponse withId(final String id) {
        return new ExpenseResponse(id, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    public String getReceiptFileId() { return receiptFileId; }
}
