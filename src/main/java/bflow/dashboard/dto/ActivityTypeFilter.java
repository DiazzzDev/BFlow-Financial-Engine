package bflow.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Direction filter for the recent activity widget.
 */
@Schema(description = "Direction filter for the recent activity widget.",
        allowableValues = {"ALL", "INCOME", "EXPENSE"})
public enum ActivityTypeFilter {
    /** Incomes and expenses. */
    ALL,
    /** Incomes only. */
    INCOME,
    /** Expenses only. */
    EXPENSE
}