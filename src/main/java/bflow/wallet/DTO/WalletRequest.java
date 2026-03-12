package bflow.wallet.DTO;

import bflow.wallet.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * Request object for creating or updating a Wallet.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletRequest {

    /** Minimum wallet name length. */
    private static final int NAME_MIN_LENGTH = 2;

    /** Maximum wallet name length. */
    private static final int NAME_MAX_LENGTH = 100;

    /** Minimum description length. */
    private static final int DESCRIPTION_MIN_LENGTH = 3;

    /** Maximum description length. */
    private static final int DESCRIPTION_MAX_LENGTH = 255;

    /** Maximum integer digits allowed for monetary values. */
    private static final int MONEY_INTEGER_DIGITS = 12;

    /** Maximum fraction digits allowed for monetary values. */
    private static final int MONEY_FRACTION_DIGITS = 2;

    /** The display name of the wallet. */
    @NotBlank(message = "Wallet name is required")
    @Size(
        min = NAME_MIN_LENGTH,
        max = NAME_MAX_LENGTH,
        message = "Wallet name must be between 2 and 100 characters"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()]+$",
            message = "Wallet name contains invalid characters"
    )
    private String name;

    /** The description of the wallet. */
    @NotBlank(message = "Wallet description is required")
    @Size(
        min = DESCRIPTION_MIN_LENGTH,
        max = DESCRIPTION_MAX_LENGTH,
        message = "Description must be between 3 and 255 characters")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()]+$",
            message = "Description contains invalid characters"
    )
    private String description;

    /** The currency of the wallet. */
    @NotNull(message = "Wallet currency is required")
    private Currency currency;

    /** The starting balance when the wallet was created. */
    @NotNull(message = "Initial value is required")
    @DecimalMin(
        value = "0.00",
        inclusive = true,
        message = "Initial value cannot be negative"
    )
    @DecimalMax(
        value = "1000000000.00",
        message = "Initial value exceeds maximum allowed"
    )
    @Digits(
        integer = MONEY_INTEGER_DIGITS,
        fraction = MONEY_FRACTION_DIGITS,
        message = "Invalid monetary format"
    )
    private BigDecimal initialValue;
}
