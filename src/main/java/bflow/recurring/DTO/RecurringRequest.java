package bflow.recurring.DTO;

import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for recurring transaction request.
 */
@Getter
@Setter
public final class RecurringRequest {

    private static final int TITLE_MIN_LENGTH = 1;

    private static final int TITLE_MAX_LENGTH = 50;

    private static final int DESCRIPTION_MAX_LENGTH = 100;

    private static final int AMOUNT_INTEGER_DIGITS = 13;

    private static final int AMOUNT_FRACTION_DIGITS = 2;

    private static final int DEFAULT_INTERVAL = 1;

    private static final int MAX_INTERVAL = 365;

    /** The transaction title. */
    @NotBlank(message = "{transaction.title.required}")
    @Size(
            min = TITLE_MIN_LENGTH, max = TITLE_MAX_LENGTH,
            message = "{transaction.title.size}"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?&@#%/+:_]+$",
            message = "{transaction.title.invalidCharacters}"
    )
    private String title;

    /** The transaction description (optional). */
    @Size(
            max = DESCRIPTION_MAX_LENGTH,
            message = "{transaction.description.size}"
    )
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?&@#%/+:_]*$",
            message = "{transaction.description.invalidCharacters}"
    )
    private String description;

    /** The transaction amount. */
    @NotNull(message = "{transaction.amount.required}")
    @Digits(
            integer = AMOUNT_INTEGER_DIGITS,
            fraction = AMOUNT_FRACTION_DIGITS,
            message = "{transaction.amount.invalidFormat}"
    )
    @DecimalMin(value = "0.01", message = "{transaction.amount.tooSmall}")
    private BigDecimal amount;

    /** The wallet ID. */
    @NotNull(message = "{transaction.walletId.required}")
    private UUID walletId;

    /** The category ID. */
    @NotNull(message = "{transaction.categoryId.required}")
    private UUID categoryId;

    /** The recurring type. */
    @NotNull(message = "{recurring.type.required}")
    private RecurringType type;

    /** The recurring frequency. */
    @NotNull(message = "{recurring.frequency.required}")
    private RecurringFrequency frequency;

    /** Repeat every N frequency units. Optional, defaults to 1. */
    @Min(value = DEFAULT_INTERVAL, message = "{recurring.intervalValue.invalid}")
    @Max(value = MAX_INTERVAL, message = "{recurring.intervalValue.invalid}")
    @Schema(description = "Repite cada N unidades de la frecuencia. "
            + "Opcional, por defecto 1.", defaultValue = "1")
    private Integer intervalValue;

    /** The start date of the recurring transaction. */
    @NotNull(message = "{recurring.startDate.required}")
    private LocalDate startDate;

    /** The end date. Null means the recurrence never ends. */
    @Schema(description = "Fecha de fin. Omitir o enviar null para una "
            + "recurrencia indefinida.", nullable = true)
    private LocalDate endDate;

    /**
     * Interval with default applied when the client omits it or sends null.
     *
     * @return the interval, never null.
     */
    public Integer getIntervalValue() {
        return intervalValue == null ? DEFAULT_INTERVAL : intervalValue;
    }

    /**
     * Cross-field rule: a non-null end date cannot precede the start date.
     *
     * @return true when the date range is valid.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "{recurring.endDate.beforeStart}")
    public boolean isEndDateValid() {
        return endDate == null
                || startDate == null
                || !endDate.isBefore(startDate);
    }
}