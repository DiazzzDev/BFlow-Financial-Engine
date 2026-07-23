package bflow.subscription.services;

import bflow.subscription.WompiApiClient;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositoryPayment;
import bflow.subscription.repository.RepositorySubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReconciliationService {

    /** Grace period before considering a checkout abandoned. */
    private static final int PENDING_GRACE_HOURS = 6;

    /** Maximum number of days to allow a pending activation before
     * expiration. */
    private static final int MAX_PENDING_DAYS = 3;

    /** Repository used to access subscription data. */
    private final RepositorySubscription repositorySubscription;

    /** Client used to read subscriber information from Wompi. */
    private final WompiApiClient wompiApiClient;

    /** Service used to activate subscriptions from trusted events. */
    private final WompiWebhookService wompiWebhookService;

    /** Repository used to access payment data. */
    private final RepositoryPayment repositoryPayment;

    /**
     * Reconcile pending activations that were not confirmed in time.
     */
    @Transactional
    public void reconcilePendingActivations() {
        Instant threshold = Instant.now().minus(
                PENDING_GRACE_HOURS,
                ChronoUnit.HOURS
        );

        List<Subscription> stale = repositorySubscription
                .findAllByStatusAndCreatedAtBefore(
                        SubscriptionStatus.PENDING_ACTIVATION,
                        threshold
                );

        log.info(
                "reconcilePendingActivations: {} suscripciones a revisar",
                stale.size()
        );

        for (Subscription subscription : stale) {
            if (subscription.getProviderLinkId() == null) {
                log.warn(
                        "Subscription {} sin providerLinkId, no se puede "
                                + "reconciliar",
                        subscription.getId()
                );
                continue;
            }
            reconcileOne(subscription);
        }
    }

    /**
     * Reconcile a single pending subscription against the provider data.
     *
     * @param subscription the pending subscription to reconcile
     */
    private void reconcileOne(final Subscription subscription) {
        try {
            List<WompiApiClient.SubscriberInfo> subscribers =
                    wompiApiClient.getSubscribers(
                            subscription.getProviderLinkId()
                    );

            String userEmail = subscription.getUser().getEmail();

            subscribers.stream()
                    .filter(subscriber -> userEmail.equalsIgnoreCase(
                            subscriber.idSuscriptor()))
                    .findFirst()
                    .ifPresentOrElse(
                            match -> handleMatch(subscription, match),
                            () -> handleNoMatch(subscription)
                    );

        } catch (Exception e) {
            log.error(
                    "Error consultando reconciliación para subscription {}: {}",
                    subscription.getId(),
                    e.getMessage()
            );
        }
    }

    /**
     * Handle a matched subscriber and decide whether to activate the
     * subscription.
     *
     * @param subscription the pending subscription
     * @param match the matching subscriber from Wompi
     */
    private void handleMatch(
            final Subscription subscription,
            final WompiApiClient.SubscriberInfo match
    ) {
        if (!"Activa".equalsIgnoreCase(match.estado())
                || match.pagosRealizados() < 1) {
            Instant hardCutoff = subscription.getCreatedAt()
                    .plus(MAX_PENDING_DAYS, ChronoUnit.DAYS);
            if (Instant.now().isAfter(hardCutoff)) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                repositorySubscription.save(subscription);
                log.warn(
                        "Subscription {} expirada tras {} días sin "
                                + "confirmación (estado Wompi={})",
                        subscription.getId(),
                        MAX_PENDING_DAYS,
                        match.estado()
                );
            } else {
                log.info(
                        "Subscription {} sin pago confirmado aún "
                                + "(estado={}, pagos={})",
                        subscription.getId(),
                        match.estado(),
                        match.pagosRealizados()
                );
            }
            return;
        }

        String syntheticPaymentId = "RECON-" + match.id();
        if (repositoryPayment.existsByProviderPaymentId(syntheticPaymentId)) {
            return;
        }

        wompiWebhookService.activate(
                subscription,
                syntheticPaymentId,
                match.monto()
        );
        log.warn(
                "Subscription {} activada vía reconciliación "
                        + "(webhook perdido). idSuscriptor={}",
                subscription.getId(),
                match.idSuscriptor()
        );
    }

    /**
     * Handle the case when no subscriber matched the checkout.
     *
     * @param subscription the pending subscription
     */
    private void handleNoMatch(final Subscription subscription) {
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        repositorySubscription.save(subscription);
        log.info(
                "Subscription {} marcada EXPIRED: sin suscriptor en Wompi tras "
                        + "el grace period",
                subscription.getId()
        );
    }
}
