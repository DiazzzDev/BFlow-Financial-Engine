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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReconciliationService {

    /** Time allowed for Wompi to expose a just-completed enrolment. */
    private static final int PENDING_GRACE_MINUTES = 5;

    /** Billing calendar used by Wompi and the BFlow merchant account. */
    private static final ZoneId WOMPI_ZONE = ZoneId.of("America/El_Salvador");

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
            if (checkoutDayHasPassed(subscription)) {
                expirePendingCheckout(subscription);
                continue;
            }
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
        log.info("Subscription {} sigue pendiente: afiliación Wompi aún "
                        + "no encontrada", subscription.getId());
    }

    /**
     * A checkout remains valid until its El Salvador calendar day ends.
     * Reconciliation marks it EXPIRED on the following day if still unpaid.
     */
    private boolean checkoutDayHasPassed(final Subscription subscription) {
        LocalDate checkoutDate = subscription.getCreatedAt()
                .atZone(WOMPI_ZONE)
                .toLocalDate();
        return LocalDate.now(WOMPI_ZONE).isAfter(checkoutDate);
    }

    private void expirePendingCheckout(final Subscription subscription) {
        if (subscription.getProviderLinkId() != null) {
            deactivateExpiredEnrollment(subscription);
        }
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        repositorySubscription.save(subscription);
        log.info(
                "Subscription {} marcada EXPIRED: checkout sin pago "
                        + "confirmado al cierre del día",
                subscription.getId()
        );
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
