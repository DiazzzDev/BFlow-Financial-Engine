package bflow.subscription.services;

import bflow.subscription.WompiApiClient;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
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

    /** Time allowed for Wompi to expose a just-completed enrolment. */
    private static final int PENDING_GRACE_MINUTES = 5;

    /** Maximum number of days for a customer to complete enrolment. */
    private static final int MAX_PENDING_ENROLLMENT_DAYS = 30;

    /** Repository used to access subscription data. */
    private final RepositorySubscription repositorySubscription;

    /** Client used to read subscriber information from Wompi. */
    private final WompiApiClient wompiApiClient;

    /**
     * Reconcile pending activations that were not confirmed in time.
     */
    @Transactional
    public void reconcilePendingActivations() {
        Instant threshold = Instant.now().minus(
                PENDING_GRACE_MINUTES, ChronoUnit.MINUTES);

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
     * Keep matched enrollments pending until an approved-payment webhook
     * arrives, or expire them after the checkout window closes.
     *
     * @param subscription the pending subscription
     * @param match the matching subscriber from Wompi
     */
    private void handleMatch(
            final Subscription subscription,
            final WompiApiClient.SubscriberInfo match
    ) {
        Instant hardCutoff = subscription.getCreatedAt()
                .plus(MAX_PENDING_ENROLLMENT_DAYS, ChronoUnit.DAYS);
        if (Instant.now().isAfter(hardCutoff)) {
            deactivateExpiredEnrollment(subscription);
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            repositorySubscription.save(subscription);
            log.warn("Subscription {} expirada tras {} días sin webhook "
                            + "de transacción aprobada (estado Wompi={})",
                    subscription.getId(), MAX_PENDING_ENROLLMENT_DAYS,
                    match.estado());
            return;
        }

        log.info("Subscription {} sigue pendiente: enrolamiento Wompi={} "
                        + "pero falta el webhook de pago aprobado",
                subscription.getId(), match.estado());
    }

    /**
     * Handle the case when no subscriber matched the checkout.
     *
     * @param subscription the pending subscription
     */
    private void handleNoMatch(final Subscription subscription) {
        Instant hardCutoff = subscription.getCreatedAt()
                .plus(MAX_PENDING_ENROLLMENT_DAYS, ChronoUnit.DAYS);
        if (Instant.now().isAfter(hardCutoff)) {
            deactivateExpiredEnrollment(subscription);
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            repositorySubscription.save(subscription);
            log.info(
                    "Subscription {} marcada EXPIRED: no se completó la "
                            + "afiliación Wompi en {} días",
                    subscription.getId(), MAX_PENDING_ENROLLMENT_DAYS
            );
            return;
        }

        log.info("Subscription {} sigue pendiente: afiliación Wompi aún "
                        + "no encontrada", subscription.getId());
    }

    private void deactivateExpiredEnrollment(
            final Subscription subscription
    ) {
        try {
            wompiApiClient.deactivateRecurringLink(
                    subscription.getProviderLinkId());
        } catch (RuntimeException exception) {
            log.error("Could not deactivate expired Wompi enrollment {}",
                    subscription.getId(), exception);
        }
    }
}
