package bflow.gmail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import bflow.gmail.enums.GmailImportStatus;
import bflow.receipts.enums.ReceiptTransactionType;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Processed Gmail attachment outcome. No email body or file bytes are returned.")
public record GmailImportResponse(
        UUID id,
        GmailImportStatus status,
        @Schema(description = "Created transaction id, either an expense or income.")
        UUID transactionId,
        @Schema(description = "Deprecated compatibility field, populated only for expenses.")
        UUID expenseId,
        @Schema(description = "Direction detected before automatic registration.")
        ReceiptTransactionType transactionType,
        String errorCode,
        Instant createdAt
) { }
