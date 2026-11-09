package bflow.gmail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

@Schema(description = "Bounded Gmail import request. Attachments are processed asynchronously.")
public record GmailSyncRequest(
        @Schema(description = "Optional wallet override. If omitted, the user's default authorized wallet is used.")
        UUID walletId,
        @Min(1) @Max(90) @Schema(description = "Search age in days (1 = last day, 7 = last week, 30 = last month; maximum 90).", example = "7")
        int lookbackDays,
        @Min(1) @Max(100) @Schema(description = "Maximum messages to inspect, between 1 and 100.", example = "50")
        int maxMessages,
        @Schema(description = "When true, discovery is reported without creating import jobs.")
        boolean dryRun
) { }
