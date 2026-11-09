package bflow.gmail.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Asynchronous Gmail import lifecycle.", allowableValues = {
        "QUEUED", "PROCESSING", "COMPLETED", "FAILED"})
public enum GmailImportStatus { QUEUED, PROCESSING, COMPLETED, FAILED }
