package bflow.wallet.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Replaces a wallet's opening balance during onboarding or correction.
 */
@Schema(description = "Request to replace a wallet's opening balance. The "
        + "current balance is adjusted by the difference, preserving existing "
        + "transaction history.")
public record OpeningBalanceRequest(
        @NotNull(message = "{transaction.amount.required}")
        @DecimalMin(value = "0.00", message = "{transaction.amount.tooSmall}")
        @DecimalMax(value = "1000000000.00",
                message = "{transaction.amount.tooLarge}")
        @Digits(integer = 12, fraction = 2,
                message = "{transaction.amount.invalidFormat}")
        @Schema(description = "Non-negative opening balance. Up to 12 integer "
                + "digits and 2 decimal places.", example = "250.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount
) { }
