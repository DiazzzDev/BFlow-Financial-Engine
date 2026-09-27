package bflow.dashboard.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Time granularity used by the dashboard statistics chart.
 */
@Schema(description = "Time range used by dashboard statistics. "
        + "YEAR returns monthly points; WEEK, MONTH, and CUSTOM return daily points.",
        allowableValues = {"WEEK", "MONTH", "YEAR", "CUSTOM"})
public enum StatisticsPeriod {
    /** Seven-day ISO week, Monday through Sunday. */
    WEEK,
    /** Calendar month. */
    MONTH,
    /** Calendar year. */
    YEAR,
    /** Explicit startDate/endDate range, limited to 366 days. */
    CUSTOM
}
