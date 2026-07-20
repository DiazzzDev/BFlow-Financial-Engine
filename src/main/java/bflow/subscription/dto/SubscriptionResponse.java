package bflow.subscription.dto;

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
    public static SubscriptionResponse from(
        final bflow.subscription.entities.Subscription s
    ) {
        return new SubscriptionResponse(
                s.getId(),
                s.getPlan().getName(),
                s.getStatus(),
                s.getBillingAmount(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getNextBillingAt());
    }
}