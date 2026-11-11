package bflow.common.financial;

import bflow.category.DTO.CategoryResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** JavaBean-style aliases for the shared transaction response record fields. */
public interface TransactionResponseAccessors {

    String id();
    String title();
    String description();
    BigDecimal amount();
    LocalDate date();
    CategoryResponse category();
    Boolean recurring();
    String walletId();
    String walletName();
    String contributorId();
    String contributorName();
    String source();
    Instant createdAt();
    Integer editCount();
    Integer categorizationChanges();
    Double confidenceScore();
    String recurrencePattern();
    String recurringTransactionId();

    default String getId() { return id(); }
    default String getTitle() { return title(); }
    default String getDescription() { return description(); }
    default BigDecimal getAmount() { return amount(); }
    default LocalDate getDate() { return date(); }
    default CategoryResponse getCategory() { return category(); }
    default Boolean getRecurring() { return recurring(); }
    default String getWalletId() { return walletId(); }
    default String getWalletName() { return walletName(); }
    default String getContributorId() { return contributorId(); }
    default String getContributorName() { return contributorName(); }
    default String getSource() { return source(); }
    default Instant getCreatedAt() { return createdAt(); }
    default Integer getEditCount() { return editCount(); }
    default Integer getCategorizationChanges() {
        return categorizationChanges();
    }
    default Double getConfidenceScore() { return confidenceScore(); }
    default String getRecurrencePattern() { return recurrencePattern(); }
    default String getRecurringTransactionId() {
        return recurringTransactionId();
    }
}
