package bflow.subscription.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment processing lifecycle state.", allowableValues = {
        "PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELED", "REFUNDED"})
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

