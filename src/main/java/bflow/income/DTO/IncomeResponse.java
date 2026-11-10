package bflow.income.DTO;

import bflow.category.DTO.CategoryResponse;

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
) {
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

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDate() { return date; }
    public CategoryResponse getCategory() { return category; }
    public String getWalletId() { return walletId; }
    public String getWalletName() { return walletName; }
    public String getContributorId() { return contributorId; }
    public String getContributorName() { return contributorName; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getEditCount() { return editCount; }
    public Integer getCategorizationChanges() { return categorizationChanges; }
    public Double getConfidenceScore() { return confidenceScore; }
    public Boolean getRecurring() { return recurring; }
    public String getRecurrencePattern() { return recurrencePattern; }
    public String getRecurringTransactionId() { return recurringTransactionId; }
}
