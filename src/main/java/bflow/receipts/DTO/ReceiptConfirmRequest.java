package bflow.receipts.DTO;

import bflow.receipts.enums.ReceiptTransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
    @Schema(description = "Transaction type to create.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ReceiptTransactionType type;

    /**
     * The title of the resulting transaction.
     */
    @NotBlank
    @Size(max = TITLE_MAX_LENGTH)
    @Schema(description = "User-facing transaction title. Must contain at "
            + "least one non-whitespace character and be at most 255 "
            + "characters.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /**
     * An optional free-text description of the resulting
     * transaction.
     */
    private String description;

    /**
     * The amount of the resulting transaction. OCR sometimes
     * represents expense totals as negative; confirmation accepts
     * either sign and stores the absolute value.
     */
    @NotNull
    @Digits(integer = 13, fraction = 2,
            message = "{transaction.amount.invalidFormat}")
    @DecimalMin(value = "-9999999999999.99",
            message = "{receipt.amount.outOfRange}")
    @DecimalMax(value = "9999999999999.99",
            message = "{receipt.amount.outOfRange}")
    @Schema(description = "Non-zero amount with up to 13 integer digits and "
            + "two decimal places. A negative OCR value is accepted and "
            + "normalized to its positive magnitude.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "500.00")
    private BigDecimal amount;

    /**
     * The category the resulting transaction belongs to. It is
     * optional because OCR cannot infer BFlow categories.
     */
    @Schema(description = "Optional category UUID. Omit or send null to create "
            + "an uncategorized transaction.", nullable = true)
    private UUID categoryId;

    /**
     * The date of the resulting transaction. It is optional when
     * OCR cannot determine it; the business-calendar date is then
     * used.
     */
    @PastOrPresent
    @Schema(description = "Optional transaction date in ISO-8601 format "
            + "(yyyy-MM-dd). If omitted or null, the server uses today's "
            + "date in America/El_Salvador. Future dates are not allowed.",
            format = "date", nullable = true, example = "2026-10-05")
    private LocalDate date;

    /**
     * Rejects zero while allowing the signed amount returned by OCR.
     *
     * @return true when the amount is omitted or non-zero
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "{receipt.amount.nonZero}")
    public boolean isAmountNonZero() {
        return amount == null || amount.signum() != 0;
    }
}
