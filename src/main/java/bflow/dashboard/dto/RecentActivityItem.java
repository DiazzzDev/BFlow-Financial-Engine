package bflow.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single item in the "recent activity" feed.
 *
 * @param type the type of activity, such as income or expense
 * @param name the name of the activity
 * @param createdAt the date and time when the activity was created
 * @param amount the activity amount,
 * negative for expenses and positive for incomes
 * @param walletName the name of the wallet associated with the activity
 * @param categoryIcon the icon identifier of the associated category
 */
public record RecentActivityItem(
        String type,
        String name,
        Instant createdAt,
        BigDecimal amount,
        String walletName,
        String categoryIcon
) { }
