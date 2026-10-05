package bflow.notifications.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for notification response.
 */
@Getter
@Setter
public class NotificationResponse {
    /**
     * The notification ID.
     */
    private UUID id;
    /**
     * The notification title.
     */
    private String title;
    /**
     * The notification message.
     */
    private String message;
    /**
     * The notification type.
     */
    @Schema(description = "Notification category.", allowableValues = {
            "BUDGET_SUCCESS", "BUDGET_GROUP_SUCCESS", "BUDGET_WARNING",
            "BUDGET_CRITICAL", "BUDGET_EXCEEDED", "GOAL_REACHED",
            "NEW_CONTRIBUTOR", "ACCOUNT_LOCKED", "WALLET_INVITATION"})
    private String type;
    /**
     * Whether the notification has been read.
     */
    private Boolean read;
    /**
     * The creation timestamp.
     */
    private Instant createdAt;

    /**
     * Related resource identifier. Present for actionable notifications; for
     * {@code WALLET_INVITATION} it is the invitation ID, otherwise null.
     */
    @Schema(nullable = true, description = "Related resource UUID. Null when "
            + "the notification has no action target.")
    private UUID referenceId;
}
