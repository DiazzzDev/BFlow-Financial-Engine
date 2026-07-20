package bflow.subscription.dto;

import java.util.UUID;

public record CheckoutResponse(
    UUID subscriptionId,
    String checkoutUrl
) {}
