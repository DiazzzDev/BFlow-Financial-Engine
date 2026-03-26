package bflow.budget.DTO;

import bflow.budget.enums.PeriodType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor
public class BudgetRequest {

    @NotNull
    private UUID walletId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private PeriodType period;

    @NotNull
    private LocalDate startDate;

    @Min(1) @Max(99)
    private Integer thresholdWarning = 70;

    @Min(1) @Max(99)
    private Integer thresholdCritical = 90;
}