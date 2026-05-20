package bflow.budget.DTO;

import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.PeriodType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for partial budget updates.
 */
@Getter
@Setter
@NoArgsConstructor
public class BudgetPatchRequest {

    /**
     * Threshold max value.
     */
    private static final int THRESHOLD_MAX = 99;

    /**
     * Updated budget amount.
     */
    @Positive
    private BigDecimal amount;

    /**
     * Updated budget period.
     */
    private PeriodType period;

    /**
     * Updated budget start date.
     */
    private LocalDate startDate;

    /**
     * Updated warning threshold.
     */
    @Min(1)
    @Max(THRESHOLD_MAX)
    private Integer thresholdWarning;

    /**
     * Updated critical threshold.
     */
    @Min(1)
    @Max(THRESHOLD_MAX)
    private Integer thresholdCritical;

    /**
     * Updated budget scope.
     */
    private BudgetScope scope;

    /**
     * Updated category ID.
     */
    private UUID categoryId;
}