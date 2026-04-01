package bflow.recurring.DTO;

import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for recurring transaction response.
 */
@Getter
@Setter
public final class RecurringResponse {

    /**
     * The recurring transaction ID.
     */
    private UUID id;
    /**
     * The transaction title.
     */
    private String title;
    /**
     * The transaction amount.
     */
    private BigDecimal amount;

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
     * The next execution date.
     */
    private LocalDate nextExecutionDate;
    /**
     * Whether the recurring is active.
     */
    private Boolean active;

    /**
     * The wallet ID.
     */
    private UUID walletId;
    /**
     * The category ID.
     */
    private UUID categoryId;
}
