package bflow.subscription.enums;

public enum SubscriptionStatus {
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
