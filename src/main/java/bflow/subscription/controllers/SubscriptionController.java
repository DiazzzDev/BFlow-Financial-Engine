package bflow.subscription.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.response.ApiResponse;
import bflow.subscription.dto.CheckoutRequest;
import bflow.subscription.dto.CheckoutResponse;
import bflow.subscription.dto.SubscriptionResponse;
import bflow.subscription.services.PaymentService;
import bflow.subscription.services.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public final class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<List<SubscriptionResponse>> mySubscriptions(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        List<SubscriptionResponse> result = 
            subscriptionService.findMySubscriptions(userId);
        return ApiResponse.success(
            "Suscripciones obtenidas", result, request.getRequestURI()
        );
    }

    @PostMapping("/checkout")
    public ApiResponse<CheckoutResponse> createCheckout(
            @Valid @RequestBody final CheckoutRequest checkoutRequest,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        CheckoutResponse result = paymentService.createCheckout(
            userId, checkoutRequest
        );
        return ApiResponse.success(
            "Checkout creado", 
            result, 
            request.getRequestURI()
        );
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        subscriptionService.cancel(userId, id);
        return ApiResponse.success(
            "Suscripción cancelada",
             null, 
             request.getRequestURI()
        );
    }
}
