package bflow.budget.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Budget status enumeration.
 */
@Schema(description = "Budget health: OK is below warning; WARNING and "
        + "CRITICAL crossed their thresholds; EXCEEDED reached 100%.",
        allowableValues = {"OK", "WARNING", "CRITICAL", "EXCEEDED"})
public enum BudgetStatus {
    /**
     * Budget is below the warning threshold.
     */
    OK,
    /**
     * Budget has exceeded the warning threshold.
     */
    WARNING,
    /**
     * Budget has exceeded the critical threshold.
     */
    CRITICAL,
    /**
     * Budget has been exceeded (100%).
     */
    EXCEEDED
}
