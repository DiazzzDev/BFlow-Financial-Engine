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

    // tiempo de gracia antes de considerar un checkout como abandonado
    private static final int PENDING_GRACE_HOURS = 6;

    private final RepositorySubscription repositorySubscription;
    private final WompiApiClient wompiApiClient;
    private final WompiWebhookService wompiWebhookService;
    private final RepositoryPayment repositoryPayment;

    @Transactional
    public void reconcilePendingActivations() {
        Instant threshold = Instant.now().minus(PENDING_GRACE_HOURS, ChronoUnit.HOURS);

        List<Subscription> stale = repositorySubscription
                .findAllByStatusAndCreatedAtBefore(SubscriptionStatus.PENDING_ACTIVATION, threshold);

        log.info("reconcilePendingActivations: {} suscripciones a revisar", stale.size());

        for (Subscription subscription : stale) {
            if (subscription.getProviderLinkId() == null) {
                log.warn("Subscription {} sin providerLinkId, no se puede reconciliar", subscription.getId());
                continue;
            }
            reconcileOne(subscription);
        }
    }

    private void reconcileOne(Subscription subscription) {
        try {
            List<WompiApiClient.SubscriberInfo> subscribers =
                    wompiApiClient.getSubscribers(subscription.getProviderLinkId());

            String userEmail = subscription.getUser().getEmail();

            subscribers.stream()
                    .filter(s -> userEmail.equalsIgnoreCase(s.idSuscriptor()))
                    .findFirst()
                    .ifPresentOrElse(
                            match -> handleMatch(subscription, match),
                            () -> handleNoMatch(subscription)
                    );

        } catch (Exception e) {
            log.error("Error consultando reconciliación para subscription {}: {}",
                    subscription.getId(), e.getMessage());
        }
    }

    private void handleMatch(Subscription subscription, WompiApiClient.SubscriberInfo match) {
        if (!"Activa".equalsIgnoreCase(match.estado()) || match.pagosRealizados() < 1) {
            // suscriptor existe en Wompi pero aún no hay cobro confirmado
            // (ej. afiliación aceptada pero primer cobro pendiente); no hacemos nada, se reintenta en el próximo ciclo
            log.info("Subscription {} tiene suscriptor en Wompi pero sin pago confirmado aún (estado={}, pagos={})",
                    subscription.getId(), match.estado(), match.pagosRealizados());
            return;
        }

        String syntheticPaymentId = "RECON-" + match.id();
        if (repositoryPayment.existsByProviderPaymentId(syntheticPaymentId)) {
            return; // ya reconciliado antes
        }

        wompiWebhookService.activate(subscription, syntheticPaymentId, match.monto());
        log.warn("Subscription {} activada vía reconciliación (webhook perdido). idSuscriptor={}",
                subscription.getId(), match.idSuscriptor());
    }

    private void handleNoMatch(Subscription subscription) {
        // nadie se afilió con este email todavía: probable checkout abandonado.
        // el grace period ya se aplicó antes de llamar reconcileOne, así que expira.
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        repositorySubscription.save(subscription);
        log.info("Subscription {} marcada EXPIRED: sin suscriptor en Wompi tras el grace period", subscription.getId());
    }
}