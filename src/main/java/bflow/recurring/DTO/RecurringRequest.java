package bflow.recurring.DTO;

import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.common.financial.BaseTransactionDetailsRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public final class RecurringRequest extends BaseTransactionDetailsRequest {

    private static final int AMOUNT_INTEGER_DIGITS = 13;

    private static final int AMOUNT_FRACTION_DIGITS = 2;

    private static final int DEFAULT_INTERVAL = 1;

    private static final int MAX_INTERVAL = 365;

    /** The transaction amount. */
    @Schema(description = "Positive amount with up to two decimal places.",
            example = "750.00")
    @NotNull(message = "{transaction.amount.required}")
    @Digits(
            integer = AMOUNT_INTEGER_DIGITS,
            fraction = AMOUNT_FRACTION_DIGITS,
            message = "{transaction.amount.invalidFormat}"
    )
    @DecimalMin(value = "0.01", message = "{transaction.amount.tooSmall}")
    private BigDecimal amount;

    /** The wallet ID; defaults to the user's default owned wallet. */
    @Schema(description = "Optional wallet UUID. When omitted or null, "
            + "the user's default owned wallet is used.", nullable = true,
            example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID walletId;

    /** Optional category ID; an uncategorized recurrence is valid. */
    @Schema(description = "Optional category UUID. Omit or send null to "
            + "create an uncategorized recurrence; it can be categorized "
            + "later.", nullable = true,
            example = "123e4567-e89b-12d3-a456-426614174001")
    private UUID categoryId;

    /** The recurring type. */
    @Schema(description = "Whether the scheduled transaction is an expense "
            + "or income.", example = "EXPENSE")
    @NotNull(message = "{recurring.type.required}")
    private RecurringType type;

    /** The recurring frequency. */
    @Schema(description = "How often the transaction repeats.",
            example = "MONTHLY")
    @NotNull(message = "{recurring.frequency.required}")
    private RecurringFrequency frequency;

    /** Repeat every N frequency units. Optional, defaults to 1. */
    @Min(value = DEFAULT_INTERVAL, message = "{recurring.intervalValue.invalid}")
    @Max(value = MAX_INTERVAL, message = "{recurring.intervalValue.invalid}")
    @Schema(description = "Repeat every N frequency units. Optional; "
            + "defaults to 1.", defaultValue = "1", example = "1")
    private Integer intervalValue;

    /** The start date; defaults to today in the business time zone. */
    @Schema(description = "Optional start date in ISO-8601 format. When "
            + "omitted or null, the backend uses today's date in "
            + "America/El_Salvador.", nullable = true, example = "2026-11-10")
    private LocalDate startDate;

    /** The end date. Null means the recurrence never ends. */
    @Schema(description = "Optional inclusive end date in ISO-8601 format. "
            + "Omit or send null for an indefinite recurrence.", nullable = true,
            example = "2027-12-31")
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
