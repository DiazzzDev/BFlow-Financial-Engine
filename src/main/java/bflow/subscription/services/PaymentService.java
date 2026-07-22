package bflow.subscription.services;

import bflow.auth.repository.RepositoryUser;
import bflow.subscription.WompiApiClient;
import bflow.subscription.dto.CheckoutRequest;
import bflow.subscription.dto.CheckoutResponse;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositoryPlan;
import bflow.subscription.repository.RepositorySubscription;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int MAX_SAFE_BILLING_DAY = 28;

    private final RepositoryPlan repositoryPlan;
    private final RepositorySubscription repositorySubscription;
    private final RepositoryUser repositoryUser;
    private final WompiApiClient wompiApiClient;

    @Transactional
    public CheckoutResponse createCheckout(final UUID userId, final CheckoutRequest request) {
        log.info("createCheckout solicitado: userId={}, planId={}", userId, request.planId());

        Plan plan = repositoryPlan.findById(request.planId())
                .orElseThrow(() -> new EntityNotFoundException("Plan no encontrado"));

        if (repositorySubscription.existsByUser_IdAndPlan_IdAndStatusIn(
                userId, plan.getId(),
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PENDING_ACTIVATION, SubscriptionStatus.PAST_DUE))) {
            throw new IllegalStateException("Ya existe una suscripción activa o pendiente para este plan");
        }

        Subscription subscription = new Subscription();
        subscription.setUser(repositoryUser.getReferenceById(userId));
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
        subscription.setBillingAmount(plan.getPrice());
        subscription.setStartsAt(Instant.now());

        if (plan.getBillingPeriod() == BillingPeriod.YEARLY) {
            String checkoutReference = UUID.randomUUID().toString();
            var link = wompiApiClient.createPaymentLink(checkoutReference, plan.getPrice(), plan.getName());

            subscription.setAutoRenew(false); // pago único: no hay cobro automático posible
            subscription.setCheckoutReference(checkoutReference);
            subscription.setProviderLinkId(String.valueOf(link.idEnlace()));
            subscription.setCheckoutUrl(link.urlEnlace());
        } else {
            int diaDePago = clampDiaDePago(LocalDate.now(ZoneOffset.UTC).getDayOfMonth());
            var link = wompiApiClient.createRecurringLink(diaDePago, plan.getName(), plan.getPrice(), plan.getName());

            subscription.setAutoRenew(true);
            subscription.setBillingDay(diaDePago);
            subscription.setProviderLinkId(link.id());
            subscription.setCheckoutUrl(link.urlCortaSuscribirse());
        }

        try {
            repositorySubscription.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Ya existe una suscripción activa o pendiente para este plan", e);
        }

        log.info("Subscription {} creada en PENDING_ACTIVATION para userId={}, planId={}, providerLinkId={}",
                subscription.getId(), userId, plan.getId(), subscription.getProviderLinkId());

        return new CheckoutResponse(subscription.getId(), subscription.getCheckoutUrl());
    }

    private int clampDiaDePago(int day) {
        return Math.min(day, MAX_SAFE_BILLING_DAY);
    }
}