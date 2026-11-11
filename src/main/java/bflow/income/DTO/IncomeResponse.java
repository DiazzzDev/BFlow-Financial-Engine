package bflow.income.DTO;

import bflow.category.DTO.CategoryResponse;
import bflow.common.financial.TransactionResponseAccessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable API response for an income. */
public record IncomeResponse(
        String id,
        String title,
        String description,
        BigDecimal amount,
        LocalDate date,
        CategoryResponse category,
        String walletId,
        String walletName,
        String contributorId,
        String contributorName,
        String source,
        Instant createdAt,
        Integer editCount,
        Integer categorizationChanges,
        Double confidenceScore,
        Boolean recurring,
        String recurrencePattern,
        String recurringTransactionId
) implements TransactionResponseAccessors {
    /** Empty response for interaction tests that only need a stub value. */
    public static IncomeResponse empty() {
        return new IncomeResponse(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null);
    }

    /** Creates a minimal response for stubbing a persisted income. */
    public static IncomeResponse withId(final String id) {
        return new IncomeResponse(id, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null);
    }

}
