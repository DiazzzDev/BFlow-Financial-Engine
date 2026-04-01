package bflow.budget.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BudgetSummaryResponse {

    private Integer total;

    private Integer ok;
    private Integer warning;
    private Integer critical;
    private Integer exceeded;

    private BigDecimal totalBudget;
    private BigDecimal totalSpent;
    private BigDecimal totalRemaining;

    private BudgetResponse highestUsage;
}
