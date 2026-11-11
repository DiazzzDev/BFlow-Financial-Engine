package bflow.recurring.DTO;

import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable API response for a recurring transaction. */
public record RecurringResponse(
        UUID id,
        String title,
        BigDecimal amount,
        RecurringType type,
        RecurringFrequency frequency,
        Integer intervalValue,
        @Schema(description = "Original recurrence start date in ISO-8601 "
                + "format. Never null.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,
        @Schema(description = "Inclusive final execution date in ISO-8601 "
                + "format. Null means the recurrence never ends.",
                nullable = true)
        LocalDate endDate,
        LocalDate nextExecutionDate,
        Boolean active,
        UUID walletId,
        UUID categoryId
) {
    /** Empty response for interaction tests that only need a stub value. */
    public static RecurringResponse empty() {
        return new RecurringResponse(null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public RecurringType getType() { return type; }
    public RecurringFrequency getFrequency() { return frequency; }
    public Integer getIntervalValue() { return intervalValue; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getNextExecutionDate() { return nextExecutionDate; }
    public Boolean getActive() { return active; }
    public UUID getWalletId() { return walletId; }
    public UUID getCategoryId() { return categoryId; }
}
