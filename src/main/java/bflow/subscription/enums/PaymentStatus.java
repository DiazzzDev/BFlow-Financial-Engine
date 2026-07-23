package bflow.subscription.enums;

public enum PaymentStatus {

    /** Payment is pending confirmation. */
    PENDING,

    /** Payment is currently being processed. */
    PROCESSING,

    /** Payment completed successfully. */
    SUCCEEDED,

    /** Payment failed before completion. */
    FAILED,

    /** Payment was canceled. */
    CANCELED,

    /** Payment was refunded. */
    REFUNDED
}

