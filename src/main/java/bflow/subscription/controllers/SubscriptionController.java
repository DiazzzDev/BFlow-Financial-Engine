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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public final class SubscriptionController {

    /** Service used to retrieve subscription state for the current user. */
    private final SubscriptionService subscriptionService;

    /** Service used to create checkout sessions for subscription plans. */
    private final PaymentService paymentService;

    /** Service used to resolve the authenticated user identifier. */
    private final CurrentUserService currentUserService;

    /**
     * Retrieve the subscriptions owned by the currently authenticated user.
     *
     * @param authentication authenticated user context
     * @param request the incoming HTTP request
     * @return a standard API response with the subscriptions list
     */
    @GetMapping
    public ApiResponse<List<SubscriptionResponse>> mySubscriptions(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        List<SubscriptionResponse> result = subscriptionService
                .findMySubscriptions(userId);
        return ApiResponse.success(
                "Suscripciones obtenidas",
                result,
                request.getRequestURI()
        );
    }

    /**
     * Create a checkout session for a plan purchase.
     *
     * @param checkoutRequest the requested checkout payload
     * @param authentication authenticated user context
     * @param request the incoming HTTP request
     * @return a standard API response with the checkout result
     */
    @PostMapping("/checkout")
    public ApiResponse<CheckoutResponse> createCheckout(
            @Valid @RequestBody final CheckoutRequest checkoutRequest,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        CheckoutResponse result = paymentService.createCheckout(
                userId,
                checkoutRequest
        );
        return ApiResponse.success(
                "Checkout creado",
                result,
                request.getRequestURI()
        );
    }

    /**
     * Cancel an existing subscription for the current user.
     *
     * @param id the subscription identifier
     * @param authentication authenticated user context
     * @param request the incoming HTTP request
     * @return a standard API response with no body
     */
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
