package bflow.dashboard.dto;

import bflow.wallet.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One income or expense row of the recent activity widget.
 */
@Schema(description = "One income or expense row of the recent activity widget.")
public record RecentActivityItem(
        @Schema(description = "Transaction UUID.") UUID id,
        @Schema(description = "EXPENSE or INCOME.",
                allowableValues = {"EXPENSE", "INCOME"}) String type,
        @Schema(description = "Transaction title.") String name,
        @Schema(description = "Business date; use it for 'hoy'/'ayer' labels.")
        LocalDate date,
        @Schema(description = "When the transaction was registered.")
        Instant createdAt,
        @Schema(description = "Signed amount: negative for expenses.")
        BigDecimal amount,
        @Schema(description = "Currency of the wallet.") Currency currency,
        @Schema(description = "Wallet name.") String walletName,
        @Schema(description = "Origin of the row, e.g. 'quick'.") String source,
        @Schema(nullable = true) String categoryIcon,
        @Schema(nullable = true) String categoryColor
) { }
