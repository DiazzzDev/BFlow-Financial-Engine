package bflow.common.financial;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public abstract class BaseTransactionRequest {


    /** Minimum length for expense title. */
    private static final int TITLE_MIN_LENGTH = 5;

    /** Maximum length for expense title. */
    private static final int TITLE_MAX_LENGTH = 50;

    /** Maximum length for expense description. */
    private static final int DESCRIPTION_MAX_LENGTH = 100;

    /** Maximum integer digits for expense amount. */
    private static final int AMOUNT_INTEGER_DIGITS = 13;

    /** Fraction digits for expense amount (decimal places). */
    private static final int AMOUNT_FRACTION_DIGITS = 2;

    /** Title of the transaction. */
    @NotBlank(message = "Title is required")
    @Size(min = TITLE_MIN_LENGTH, max = TITLE_MAX_LENGTH)
    @Pattern(regexp = "^[\\p{L}0-9 .,'\\-()!?]+$")
    private String title;

    /** Optional description. */
    @Size(max = DESCRIPTION_MAX_LENGTH)
    @Pattern(regexp = "^[\\p{L}0-9 .,'\\-()!?]*$")
    private String description;

    /** Transaction amount. */
    @NotNull
    @Digits(integer = AMOUNT_INTEGER_DIGITS, fraction = AMOUNT_FRACTION_DIGITS)
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "999999999999999.99")
    private BigDecimal amount;

    /** Transaction date. */
    @NotNull
    @PastOrPresent
    private LocalDate date;

    /** Associated wallet id. */
    @NotNull
    private UUID walletId;

    /** Indicates the money source. */
    private String source;

    /** Indicates if transaction recurs. */
    @NotNull
    private Boolean recurring = false;

    /** Recurrence pattern. */
    @Pattern(regexp = "^(DAILY|WEEKLY|MONTHLY|YEARLY)?$")
    private String recurrencePattern;

    /** Associated category id. */
    @NotNull(message = "Category ID is required")
    private UUID categoryId;

}
