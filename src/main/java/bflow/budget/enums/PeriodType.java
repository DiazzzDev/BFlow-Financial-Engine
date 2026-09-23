package bflow.budget.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Period type enumeration for budgets.
 */
@Schema(description = "Recurrence period used by a budget.",
        allowableValues = {"DAILY", "WEEKLY", "MONTHLY"})
public enum PeriodType {
    /**
     * Daily period.
     */
    DAILY,
    /**
     * Weekly period.
     */
    WEEKLY,
    /**
     * Monthly period.
     */
    MONTHLY
}
