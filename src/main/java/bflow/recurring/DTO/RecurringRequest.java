package bflow.recurring.DTO;

import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
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

    /**
     * The transaction title.
     */
    private String title;
    /**
     * The transaction description.
     */
    private String description;
    /**
     * The transaction amount.
     */
    private BigDecimal amount;

    /**
     * The wallet ID.
     */
    private UUID walletId;
    /**
     * The category ID.
     */
    private UUID categoryId;

    /**
     * The recurring type.
     */
    private RecurringType type;
    /**
     * The recurring frequency.
     */
    private RecurringFrequency frequency;

    /**
     * The interval value for custom frequencies.
     */
    private Integer intervalValue;

    /**
     * The start date of the recurring transaction.
     */
    private LocalDate startDate;
    /**
     * The end date of the recurring transaction.
     */
    private LocalDate endDate;
}
