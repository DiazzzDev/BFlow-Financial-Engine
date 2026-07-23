package bflow.subscription.dto;

import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String planName,
        SubscriptionStatus status,
        BigDecimal billingAmount,
        Instant startsAt,
        Instant endsAt,
        Instant nextBillingAt
) {
    /**
     * Build a response view from the persistence entity.
     *
     * @param subscription the subscription entity
     * @return a public-facing subscription response
     */
    public static SubscriptionResponse from(final Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.getBillingAmount(),
                subscription.getStartsAt(),
                subscription.getEndsAt(),
                subscription.getNextBillingAt()
        );
    }
}
