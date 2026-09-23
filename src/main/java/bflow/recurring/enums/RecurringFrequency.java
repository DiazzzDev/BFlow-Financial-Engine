package bflow.recurring.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Recurring frequency enumeration.
 */
@Schema(description = "Frequency used to schedule a recurring transaction.",
        allowableValues = {"DAILY", "WEEKLY", "MONTHLY"})
public enum RecurringFrequency {
    /**
     * Daily frequency.
     */
    DAILY,
    /**
     * Weekly frequency.
     */
    WEEKLY,
    /**
     * Monthly frequency.
     */
    MONTHLY
}
