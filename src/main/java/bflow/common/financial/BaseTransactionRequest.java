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
    @NotBlank(message = "{transaction.title.required}")
    @Size(
            min = TITLE_MIN_LENGTH, max = TITLE_MAX_LENGTH,
            message = "{transaction.title.size}"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?]+$",
            message = "{transaction.title.invalidCharacters}"
    )
    private String title;

    /** Optional description. */
    @Size(
            max = DESCRIPTION_MAX_LENGTH,
            message = "{transaction.description.size}"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?]*$",
            message = "{transaction.description.invalidCharacters}"
    )
    private String description;

    /** Transaction amount. */
    @NotNull(message = "{transaction.amount.required}")
    @Digits(
            integer = AMOUNT_INTEGER_DIGITS, fraction = AMOUNT_FRACTION_DIGITS,
            message = "{transaction.amount.invalidFormat}"
    )
    @DecimalMin(value = "0.01", message = "{transaction.amount.tooSmall}")
    @DecimalMax(
            value = "999999999999999.99",
            message = "{transaction.amount.tooLarge}"
    )
    private BigDecimal amount;

    /** Transaction date. */
    @NotNull(message = "{transaction.date.required}")
    @PastOrPresent(message = "{transaction.date.futureNotAllowed}")
    private LocalDate date;

    /** Associated wallet id. */
    @NotNull(message = "{transaction.walletId.required}")
    private UUID walletId;

    /** Indicates the money source. */
    private String source;

    /** Indicates if transaction recurs. */
    @NotNull(message = "{transaction.recurring.required}")
    private Boolean recurring = false;

    /** Recurrence pattern. */
    @Pattern(
            regexp = "^(DAILY|WEEKLY|MONTHLY|YEARLY)?$",
            message = "{transaction.recurrencePattern.invalid}"
    )
    private String recurrencePattern;

    /** Associated category id. */
    @NotNull(message = "{transaction.categoryId.required}")
    private UUID categoryId;

}
