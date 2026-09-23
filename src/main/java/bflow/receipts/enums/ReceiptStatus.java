package bflow.receipts.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle status of a {@link bflow.receipts.entity.ReceiptUpload}.
 */
@Schema(description = "Receipt OCR lifecycle: RECEIVED waits for OCR; "
        + "PROCESSING is in progress; EXTRACTED awaits review; CONFIRMED "
        + "created a transaction; FAILED could not be processed; DISCARDED "
        + "was rejected by the user.", allowableValues = {"RECEIVED",
        "PROCESSING", "EXTRACTED", "CONFIRMED", "FAILED", "DISCARDED"})
public enum ReceiptStatus {

    /**
     * Registered with a wallet, waiting for OCR to run.
     */
    RECEIVED,

    /**
     * Textract is currently processing the receipt image.
     */
    PROCESSING,

    /**
     * OCR finished and its suggestion was shown to the user, who
     * has not yet confirmed or discarded it.
     */
    EXTRACTED,

    /**
     * The user confirmed the suggestion; the resulting Expense or
     * Income already exists.
     */
    CONFIRMED,

    /**
     * OCR failed or produced nothing usable.
     */
    FAILED,

    /**
     * The user rejected the suggestion; nothing was created.
     */
    DISCARDED
}
