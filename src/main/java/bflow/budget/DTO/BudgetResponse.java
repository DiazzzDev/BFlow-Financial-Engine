package bflow.budget.DTO;

import bflow.budget.enums.BudgetStatus;
import bflow.budget.enums.PeriodType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter
public class BudgetResponse {
    private UUID id;
    private UUID walletId;
    private PeriodType period;
    private LocalDate startDate;

    private BigDecimal budgetLimit;   // renombrado: "budget" era ambiguo
    private BigDecimal spent;
    private BigDecimal remaining;
    private Integer percentage;

    private BudgetStatus status;      // enum, no String → type-safe
    private Integer thresholdWarning;
    private Integer thresholdCritical;

    private Instant createdAt;
}
