package bflow.tranfers.DTO;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for transfer request data.
 */
@Getter
@Setter
public class TransferenceRequest {
    /** Maximum integer digits for money values. */
    private static final int MONEY_INTEGER_DIGITS = 12;

    /** Maximum fraction digits for money values. */
    private static final int MONEY_FRACTION_DIGITS = 2;

    /** Maximum description length. */
    private static final int DESCRIPTION_MAX_LENGTH = 255;

    /** The UUID of the source wallet. */
    @NotNull(message = "Source wallet id is required")
    private UUID fromWalletId;

    /** The UUID of the destination wallet. */
    @NotNull(message = "Destination wallet id is required")
    private UUID toWalletId;

    /** The transfer amount. */
    @NotNull(message = "Transfer amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "1000000000.00",
    message = "Amount exceeds maximum allowed")
    @Digits(
        integer = MONEY_INTEGER_DIGITS,
        fraction = MONEY_FRACTION_DIGITS,
        message = "Amount must have up to 12 digits and 2 decimals"
    )
    private BigDecimal amount;

    /** Optional transfer description. */
    @Size(
        max = DESCRIPTION_MAX_LENGTH,
        message = "Description must not exceed 255 characters"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()]*$",
            message = "Description contains invalid characters"
    )
    private String description;

}
