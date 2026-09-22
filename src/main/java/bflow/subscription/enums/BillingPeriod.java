package bflow.subscription.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Recurring subscription billing cadence.",
        allowableValues = {"MONTHLY", "YEARLY"})
public enum BillingPeriod {

    /** Monthly recurring billing frequency. */
    MONTHLY,

    /** Yearly recurring billing frequency. */
    YEARLY
}
