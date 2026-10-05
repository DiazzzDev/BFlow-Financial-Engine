package bflow.receipts.DTO;

import bflow.receipts.enums.ReceiptStatus;
import bflow.receipts.enums.ReceiptTransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload returned after a receipt upload is created,
 * confirmed, or fetched.
 */
@Getter
@AllArgsConstructor
public class ReceiptUploadResponse {

    /**
     * The unique identifier of the receipt upload.
     */
    private final UUID id;

    /**
     * The identifier of the uploaded receipt image file.
     */
    private final UUID fileId;

    /**
     * The identifier of the wallet this receipt was uploaded
     * against.
     */
    private final UUID walletId;

    /**
     * The current lifecycle status of the receipt upload.
     */
    private final ReceiptStatus status;

    /**
     * Textract's suggested transaction type, once EXTRACTED. The
     * user can override this on confirm — always EXPENSE today,
     * since {@code AnalyzeExpense} has no concept of income.
     */
    private final ReceiptTransactionType suggestedType;

    /**
     * Textract's suggested title (vendor/merchant name), once
     * EXTRACTED. Editable draft — not final until confirmed.
     */
    private final String suggestedTitle;

    /**
     * Textract's suggested amount, once EXTRACTED. It is always a
     * positive magnitude even when OCR read a charge with a minus
     * sign. Editable draft — not final until confirmed.
     */
    private final BigDecimal suggestedAmount;

    /**
     * Textract's suggested category, once EXTRACTED. Currently
     * always {@code null} — Textract has no concept of category,
     * this is reserved for future merchant-based inference.
     */
    private final UUID suggestedCategoryId;

    /**
     * Textract's suggested date, once EXTRACTED. Editable draft —
     * not final until confirmed.
     */
    private final LocalDate suggestedDate;

    /**
     * Average confidence Textract reported for the fields used
     * above, 0-100. Lets the frontend flag a low-confidence
     * suggestion for closer review instead of treating every
     * suggestion as equally trustworthy.
     */
    private final BigDecimal confidenceScore;

    /**
     * Why processing failed, when {@code status} is {@code FAILED}.
     * {@code null} otherwise.
     */
    private final String failureReason;

    /**
     * The identifier of the Expense or Income this receipt resulted
     * in, once confirmed; {@code null} otherwise.
     */
    private final UUID resultingExpenseId;

    /**
     * The timestamp at which the receipt upload was created.
     */
    private final Instant createdAt;
}
