package bflow.tranfers.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of possible transfer statuses.
 */
@Schema(description = "Transfer processing state.",
        allowableValues = {"COMPLETED"})
public enum TransferStatus {
    /** Transfer has been completed successfully. */
    COMPLETED
}
