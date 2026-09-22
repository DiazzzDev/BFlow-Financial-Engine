package bflow.subscription.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Subscription lifecycle: PENDING_ACTIVATION awaits "
        + "payment confirmation; ACTIVE is usable; EXPIRED ended; CANCELED "
        + "was cancelled; PAST_DUE has an overdue renewal.", allowableValues = {
        "PENDING_ACTIVATION", "ACTIVE", "EXPIRED", "CANCELED", "PAST_DUE"})
public enum SubscriptionStatus {
    /** Subscription is waiting for confirmation. */
    PENDING_ACTIVATION,

    /** Subscription is currently active. */
    ACTIVE,

    /** Subscription has expired. */
    EXPIRED,

    /** Subscription has been cancelled. */
    CANCELED,

    /** Subscription payment is past due. */
    PAST_DUE
}

