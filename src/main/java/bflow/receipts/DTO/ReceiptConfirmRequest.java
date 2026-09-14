package bflow.receipts.DTO;

import bflow.receipts.enums.ReceiptTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What the user actually confirms after reviewing OCR's suggestion.
 * The frontend pre-fills this from the receipt's suggested_* fields
 * and lets the user edit anything Textract got wrong before
 * submitting — the backend never falls back to the suggestion
 * silently, it only persists what's sent here.
 */
@Getter
@Setter
public class ReceiptConfirmRequest {

    /**
     * Maximum allowed length for {@link #title}.
     */
    private static final int TITLE_MAX_LENGTH = 255;

    /**
     * Whether the confirmed transaction is an Expense or an Income.
     */
    @NotNull
    private ReceiptTransactionType type;

    /**
     * The title of the resulting transaction.
     */
    @NotBlank
    @Size(max = TITLE_MAX_LENGTH)
    private String title;

    /**
     * An optional free-text description of the resulting
     * transaction.
     */
    private String description;

    /**
     * The amount of the resulting transaction.
     */
    @NotNull
    @Positive
    private BigDecimal amount;

    /**
     * The category the resulting transaction belongs to.
     */
    @NotNull
    private UUID categoryId;

    /**
     * The date of the resulting transaction.
     */
    @NotNull
    private LocalDate date;
}
