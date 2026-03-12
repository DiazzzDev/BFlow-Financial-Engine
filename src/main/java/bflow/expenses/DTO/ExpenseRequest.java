package bflow.expenses.DTO;

import bflow.expenses.enums.ExpenseType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for creating or updating expense entries.
 */
@Getter
@Setter
public class ExpenseRequest {

    /** Minimum length for expense title. */
    private static final int TITLE_MIN_LENGTH = 5;
    /** Maximum length for expense title. */
    private static final int TITLE_MAX_LENGTH = 50;
    /** Maximum length for expense description. */
    private static final int DESCRIPTION_MAX_LENGTH = 100;
    /** Maximum integer digits for expense amount. */
    private static final int AMOUNT_INTEGER_DIGITS = 15;
    /** Fraction digits for expense amount (decimal places). */
    private static final int AMOUNT_FRACTION_DIGITS = 2;

    /** The title of the expense. */
    @NotBlank(message = "Expense title is required")
    @Size(min = TITLE_MIN_LENGTH, max = TITLE_MAX_LENGTH,
            message = "Title must be between 5 and 50 characters")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?]+$",
            message = "Title contains invalid characters"
    )
    private String title;

    /** Optional description of the expense. */
    @Size(max = DESCRIPTION_MAX_LENGTH,
            message = "Description cannot exceed 100 characters")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-()!?]*$",
            message = "Description contains invalid characters"
    )
    private String description;

    /** The amount of the expense. */
    @NotNull(message = "Amount is required")
    @Digits(integer = AMOUNT_INTEGER_DIGITS,
            fraction = AMOUNT_FRACTION_DIGITS,
            message = "Amount format is invalid")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999999999.99",
            message = "Amount exceeds allowed limit")
    private BigDecimal amount;

    /** The date when the expense occurred. */
    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Expense date cannot be in the future")
    private LocalDate date;

    /** The ID of the wallet associated with this expense. */
    @NotNull(message = "Wallet id is required")
    private UUID walletId;

    /** The type/category of the expense. */
    @NotNull(message = "Expense type is required")
    private ExpenseType type;

    /** Indicates whether the expense is tax deductible. */
    @NotNull
    private Boolean taxDeductible = false;

    /** Indicates whether the expense is recurring. */
    @NotNull
    private Boolean recurring = false;

    /** The recurrence pattern if the expense is recurring. */
    @Pattern(
            regexp = "^(DAILY|WEEKLY|MONTHLY|YEARLY)?$",
            message = "Invalid recurrence pattern"
    )
    private String recurrencePattern;

    /** Indicates whether the expense is reimbursable. */
    @NotNull
    private Boolean reimbursable = false;

}
