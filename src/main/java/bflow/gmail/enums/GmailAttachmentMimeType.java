package bflow.gmail.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Attachment MIME types that may be inspected.", allowableValues = {
        "application/pdf", "image/jpeg", "image/png"})
public enum GmailAttachmentMimeType { PDF, JPEG, PNG }
