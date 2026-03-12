package bflow.income.DTO;

import bflow.income.enums.IncomeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Data transfer object for creating or updating income entries.
 */
@Getter
@Setter
public class IncomeRequest {
    /**
     * Minimum length for income title.
     */
    private static final int TITLE_MIN_LENGTH = 5;

    /**
     * Maximum length for income title.
     */
    private static final int TITLE_MAX_LENGTH = 50;

    /**
     * Maximum length for income description.
     */
    private static final int DESCRIPTION_MAX_LENGTH = 100;

    /**
     * Integer digits precision for amount field.
     */
    private static final int AMOUNT_INTEGER_DIGITS = 15;

    /**
     * Fractional digits precision for amount field.
     */
    private static final int AMOUNT_FRACTION_DIGITS = 2;

    /**
     * Title of the income entry.
     */
    @NotBlank(message = "Income title is required")
    @Size(min = TITLE_MIN_LENGTH, max = TITLE_MAX_LENGTH,
            message = "Title must be between 5 and 50 characters")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?]+$",
            message = "Title contains invalid characters"
    )
    private String title;

    /**
     * Optional description of the income entry.
     */
    @Size(max = DESCRIPTION_MAX_LENGTH,
            message = "Description cannot exceed 100 characters")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?]*$",
            message = "Description contains invalid characters"
    )
    private String description;

    /**
     * Amount of the income.
     */
    @NotNull(message = "Amount is required")
    @Digits(integer = AMOUNT_INTEGER_DIGITS,
            fraction = AMOUNT_FRACTION_DIGITS,
            message = "Amount format is invalid")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999999999.99",
            message = "Amount exceeds allowed limit")
    private BigDecimal amount;

    /**
     * Date of the income transaction.
     */
    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Income date cannot be in the future")
    private LocalDate date;

    /**
     * Identifier of the wallet associated with this income.
     */
    @NotNull(message = "Wallet id is required")
    private UUID walletId;

    /**
     * Type of income (e.g., salary, bonus, investment).
     */
    @NotNull(message = "Income type is required")
    private IncomeType type;

    /**
     * Whether this income is taxable.
     */
    @NotNull
    private Boolean taxable = false;

    /**
     * Whether this income recurs.
     */
    @NotNull
    private Boolean recurring = false;

    /**
     * Pattern for recurring income (e.g., MONTHLY, YEARLY).
     */
    @Pattern(
            regexp = "^(DAILY|WEEKLY|MONTHLY|YEARLY)?$",
            message = "Invalid recurrence pattern"
    )
    private String recurrencePattern;
}
