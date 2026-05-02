package bflow.expenses.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Quick expense request DTO.
 */
@Data
public class QuickExpenseRequest {

    /**
     * The expense amount.
     */
    @NotNull
    @Positive
    private BigDecimal amount;

    /**
     * The expense description.
     */
    private String description;
}
