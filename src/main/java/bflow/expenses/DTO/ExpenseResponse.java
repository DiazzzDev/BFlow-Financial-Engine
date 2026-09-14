package bflow.expenses.DTO;

import bflow.category.DTO.CategoryResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO for expense response in API calls.
 */
@Getter
@Setter
public class ExpenseResponse {

    /**
     * The unique identifier for the expense.
     */
    private String id;

    /**
     * The title of the expense.
     */
    private String title;

    /**
     * The description of the expense.
     */
    private String description;

    /**
     * The amount of the expense.
     */
    private BigDecimal amount;

    /**
     * The date when the expense occurred.
     */
    private LocalDate date;

    /**
     * The category of the expense.
     */
    private CategoryResponse category;

    /**
     * Flag indicating if the expense is recurring.
     */
    private Boolean recurring;

    /**
     * The ID of the wallet containing this expense.
     */
    private String walletId;

    /**
     * The name of the wallet containing this expense.
     */
    private String walletName;

    /**
     * The ID of the user who contributed this expense.
     */
    private String contributorId;

    /**
     * The name of the user who contributed this expense.
     */
    private String contributorName;

    /**
     * The source of the expense (e.g., manual, receipt, voice, import).
     */
    private String source;

    /**
     * The timestamp when the expense was created.
     */
    private Instant createdAt;

    /**
     * The number of times this expense entry has been edited.
     */
    private Integer editCount;

    /**
     * The number of times the expense category has been changed.
     */
    private Integer categorizationChanges;

    /**
     * The confidence score for the expense categorization.
     */
    private Double confidenceScore;

    /**
     * Recurrence pattern of the transaction, if applicable.
     */
    private String recurrencePattern;

    /**
     * Identifier of the recurring transaction associated with this expense.
     */
    private String recurringTransactionId;

    /**
     * Identifier of the receipt file associated with this expense.
     */
    private String receiptFileId;
}
