package bflow.expenses.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Minimal quick-expense request. The amount is the only accepted input;
 * title, description, category, date, and wallet are inferred by the server.
 */
@Getter
@Setter
public class QuickExpenseRequest {

    /**
     * The expense amount.
     */
    @NotNull(message = "{transaction.amount.required}")
    @Digits(integer = 13, fraction = 2,
            message = "{transaction.amount.invalidFormat}")
    @DecimalMin(value = "0.01", message = "{transaction.amount.tooSmall}")
    @Schema(description = "Positive expense amount. At most 13 integer digits "
            + "and 2 decimal places.", example = "12.50", requiredMode =
            Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
