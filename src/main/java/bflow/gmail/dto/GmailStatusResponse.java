package bflow.gmail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import bflow.gmail.enums.GmailIntegrationStatus;

@Schema(description = "Gmail connection status. Credentials are never included.")
public record GmailStatusResponse(boolean connected, GmailIntegrationStatus status) { }
