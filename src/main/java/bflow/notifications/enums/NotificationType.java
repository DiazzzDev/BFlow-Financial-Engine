package bflow.notifications.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Notification type enumeration.
 */
@Schema(description = "Kind of notification delivered to a user.",
        allowableValues = {"BUDGET_SUCCESS", "BUDGET_GROUP_SUCCESS",
        "BUDGET_WARNING", "BUDGET_CRITICAL", "BUDGET_EXCEEDED",
        "GOAL_REACHED", "NEW_CONTRIBUTOR", "ACCOUNT_LOCKED"})
public enum NotificationType {
    /**
     * Budget success notification.
     */
    BUDGET_SUCCESS,
    /**
     * Group budget success notification, sent to every member of a
     * shared wallet when the wallet stays within budget as a team.
     */
    BUDGET_GROUP_SUCCESS,
    /**
     * Budget warning notification.
     */
    BUDGET_WARNING,
    /**
     * Budget critical notification.
     */
    BUDGET_CRITICAL,
    /**
     * Budget exceeded notification.
     */
    BUDGET_EXCEEDED,
    /**
     * Goal reached notification (Future feature).
     */
    GOAL_REACHED,
    /**
     * New contributor notification (Future feature).
     */
    NEW_CONTRIBUTOR,
    /**
     * Account locked notification.
     */
    ACCOUNT_LOCKED
}
