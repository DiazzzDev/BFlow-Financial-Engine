package bflow.subscription.dto;

import java.util.UUID;

public record CheckoutRequest(
        UUID planId
) { }
