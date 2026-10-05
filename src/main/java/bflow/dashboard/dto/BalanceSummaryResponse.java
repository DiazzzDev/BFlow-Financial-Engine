package bflow.dashboard.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current balance plus this month's income and expenses.")
public record BalanceSummaryResponse(
        @Schema(description = "Balance of the selected wallet, or of every "
                + "wallet the caller belongs to when no wallet is selected.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal total,
        @Schema(nullable = true, description = "Change vs the balance at the "
                + "start of the month. Null when a wallet filter is applied, "
                + "because transfers make the figure unreliable per wallet.")
        Double percentageChangeLastMonth,
        @Schema(description = "Income registered this month in the selected "
                + "wallet, or across all wallets when no wallet is selected.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal monthIncome,
        @Schema(description = "Expenses registered this month in the selected "
                + "wallet, or across all wallets when no wallet is selected.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal monthExpenses
) { }
