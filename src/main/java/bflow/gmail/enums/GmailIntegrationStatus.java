package bflow.gmail.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Gmail OAuth connection state.", allowableValues = {"CONNECTED", "DISCONNECTED", "REVOKED"})
public enum GmailIntegrationStatus { CONNECTED, DISCONNECTED, REVOKED }
