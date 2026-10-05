package bflow.dashboard.dto;

import bflow.budget.enums.BudgetStatus;
import bflow.wallet.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Current health of one of the user's most recently "
        + "updated budgets. Monetary amounts use the listed currency.")
public record BudgetHealthItem(
        @Schema(description = "Budget UUID.", requiredMode =
                Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "User-facing budget name. Never null; the server "
                + "uses a scope-based fallback when no custom name exists.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @Schema(description = "Last budget update in ISO-8601 UTC instant "
                + "format.", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(description = "Calculated status based on current spending, not "
                + "only the last alert sent.", requiredMode =
                Schema.RequiredMode.REQUIRED)
        BudgetStatus status,
        @Schema(description = "ISO 4217 currency shared by every monetary "
                + "field in this item.", requiredMode =
                Schema.RequiredMode.REQUIRED)
        Currency currency,
        @Schema(description = "Configured budget cap. Always positive.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal budgetLimit,
        @Schema(description = "Amount spent during the active budget period. "
                + "Zero when no matching expenses exist.", requiredMode =
                Schema.RequiredMode.REQUIRED)
        BigDecimal spent,
        @Schema(description = "budgetLimit minus spent. Negative means the "
                + "budget has been exceeded.", requiredMode =
                Schema.RequiredMode.REQUIRED)
        BigDecimal remaining,
        @Schema(description = "Whole percentage of budgetLimit already spent. "
                + "Values above 100 indicate an exceeded budget.", requiredMode =
                Schema.RequiredMode.REQUIRED)
        Integer percentage
) { }
