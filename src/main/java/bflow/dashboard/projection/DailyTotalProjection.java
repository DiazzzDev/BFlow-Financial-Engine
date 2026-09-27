package bflow.dashboard.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection for date-grouped totals used by short dashboard ranges.
 */
public interface DailyTotalProjection {

    /**
     * Date represented by the aggregation row.
     *
     * @return transaction date
     */
    LocalDate getDate();

    /**
     * Sum for the date.
     *
     * @return aggregated amount
     */
    BigDecimal getTotal();
}
