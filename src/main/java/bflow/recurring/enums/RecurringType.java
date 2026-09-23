package bflow.recurring.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Recurring transaction type enumeration.
 */
@Schema(description = "Financial direction of a recurring transaction.",
        allowableValues = {"EXPENSE", "INCOME"})
public enum RecurringType {
    /**
     * Expense type.
     */
    EXPENSE,
    /**
     * Income type.
     */
    INCOME
}
