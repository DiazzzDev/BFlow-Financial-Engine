package bflow.subscription.dto;

import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.mapper.SubscriptionMapper;
import org.mapstruct.factory.Mappers;

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
    /** Generated mapper retained behind this record's existing factory. */
    private static final SubscriptionMapper MAPPER =
            Mappers.getMapper(SubscriptionMapper.class);

    /**
     * Build a response view from the persistence entity.
     *
     * @param subscription the subscription entity
     * @return a public-facing subscription response
     */
    public static SubscriptionResponse from(final Subscription subscription) {
        return MAPPER.toResponse(subscription);
    }
}
