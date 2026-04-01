package bflow.budget.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO for budget summary response.
 */
@Getter
@Setter
@NoArgsConstructor
public final class BudgetSummaryResponse {

    /**
     * Total number of budgets.
     */
    private Integer total;

    /**
     * Count of budgets in OK status.
     */
    private Integer ok;
    /**
     * Count of budgets in WARNING status.
     */
    private Integer warning;
    /**
     * Count of budgets in CRITICAL status.
     */
    private Integer critical;
    /**
     * Count of budgets in EXCEEDED status.
     */
    private Integer exceeded;

    /**
     * Total budget amount.
     */
    private BigDecimal totalBudget;
    /**
     * Total amount spent.
     */
    private BigDecimal totalSpent;
    /**
     * Total remaining budget.
     */
    private BigDecimal totalRemaining;

    /**
     * Budget with highest usage percentage.
     */
    private BudgetResponse highestUsage;
}
