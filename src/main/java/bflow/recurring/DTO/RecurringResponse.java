package bflow.recurring.DTO;

import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import io.swagger.v3.oas.annotations.media.Schema;
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

    /** Original date from which the recurrence pattern is calculated. */
    @Schema(description = "Original recurrence start date in ISO-8601 format. "
            + "Never null.", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate startDate;

    /**
     * Inclusive final execution date, or {@code null} when the recurrence
     * continues indefinitely.
     */
    @Schema(description = "Inclusive final execution date in ISO-8601 format. "
            + "Null means the recurrence never ends.", nullable = true)
    private LocalDate endDate;

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
