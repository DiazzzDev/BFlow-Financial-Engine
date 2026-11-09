package bflow.gmail.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Summary of Gmail discovery. OCR processing continues in the background.")
public record GmailSyncResponse(
        int messagesFound,
        @Schema(description = "Compatible attachments and recognizable transactional email bodies.")
        int compatibleAttachments,
        int duplicates,
        int queuedForProcessing,
        int partialErrors
) { }
