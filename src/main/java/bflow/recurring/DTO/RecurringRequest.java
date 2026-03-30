package bflow.recurring.DTO;

import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter
public class RecurringRequest {

    private String title;
    private String description;
    private BigDecimal amount;

    private UUID walletId;
    private UUID categoryId;

    private RecurringType type;
    private RecurringFrequency frequency;

    private Integer intervalValue;

    private LocalDate startDate;
    private LocalDate endDate;
}
