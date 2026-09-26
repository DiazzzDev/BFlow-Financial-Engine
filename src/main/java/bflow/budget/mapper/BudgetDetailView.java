package bflow.budget.mapper;

import bflow.budget.DTO.RecentActivityItem;
import bflow.budget.DTO.SpendingTrendPoint;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetStatus;
import bflow.wallet.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Mapping context for the calculated budget detail response.
 *
 * <p>The service remains responsible for calculating these values; the
 * mapper is responsible only for assembling the response DTO.</p>
 *
 * @param budget persisted budget metadata
 * @param currency currency resolved for the budget scope
 * @param status calculated budget status
 * @param startDate start of the evaluated period
 * @param endDate end of the evaluated period
 * @param daysLeft days remaining in the period
 * @param daysElapsed days elapsed in the evaluated range
 * @param budgetLimit calculated budget limit
 * @param spent calculated amount spent
 * @param remaining calculated remaining amount
 * @param percentage calculated usage percentage
 * @param thresholdWarning warning threshold
 * @param thresholdCritical critical threshold
 * @param transactionCount number of transactions in the evaluated range
 * @param averageDailySpend average daily spend
 * @param projectedTotal projected period spend
 * @param spendingTrend cumulative spending trend
 * @param recentActivity recent budget activity
 */
public record BudgetDetailView(
        Budget budget,
        Currency currency,
        BudgetStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int daysLeft,
        int daysElapsed,
        BigDecimal budgetLimit,
        BigDecimal spent,
        BigDecimal remaining,
        double percentage,
        Integer thresholdWarning,
        Integer thresholdCritical,
        int transactionCount,
        BigDecimal averageDailySpend,
        BigDecimal projectedTotal,
        List<SpendingTrendPoint> spendingTrend,
        List<RecentActivityItem> recentActivity
) {
}
