package bflow.subscription.services;

import bflow.auth.entities.User;
import bflow.subscription.dto.SubscriptionResponse;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final int DAYS_PER_YEAR = 365;
    private static final int DAYS_PER_MONTH = 30;

    private final RepositorySubscription repositorySubscription;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> findMySubscriptions(final UUID userId) {
        return repositorySubscription.findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(SubscriptionResponse::from).toList();
    }

    @Transactional
    public void cancel(final UUID userId, final UUID subscriptionId) {
        Subscription subscription = repositorySubscription
            .findById(subscriptionId)
                .orElseThrow(() -> new EntityNotFoundException(
                    "Suscripción no encontrada"
                ));

        if (!subscription.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(
                "No autorizado para cancelar esta suscripción"
            );
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return; // idempotente: cancelar dos veces no es error
        }

        // LIMITACIÓN CONOCIDA: esto solo cancela localmente. Wompi no ofrece
        // (según su documentación pública) un endpoint para dar de baja a un
        // suscriptor individual sin desactivar el enlace completo del plan.
        // Confirmar con soporte de Wompi antes de ir a producción.
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(Instant.now());
        subscription.setAutoRenew(false);
        subscription.setEndsAt(Instant.now());
        repositorySubscription.save(subscription);
    }

    /**
     * Create a free subscription for a newly registered user.
     *
     * @param user the user entity
     * @return the created or existing free subscription
     */
    @Transactional
    public Subscription createFreeSubscription(final User user) {

        if (repositorySubscription.existsByUserId(user.getId())) {
            return repositorySubscription
                    .findByUserId(user.getId())
                    .orElseThrow();
        }

        Plan freePlan = planService.getFreePlan();

        Subscription subscription = new Subscription();

        subscription.setUser(user);
        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingAmount(freePlan.getPrice());

        Instant now = Instant.now();
        subscription.setStartsAt(now);

        if (freePlan.getBillingPeriod() == BillingPeriod.YEARLY) {
            Instant ends = now.plus(DAYS_PER_YEAR, ChronoUnit.DAYS);
            subscription.setEndsAt(ends);
            subscription.setNextBillingAt(ends);
        } else {
            Instant ends = now.plus(DAYS_PER_MONTH, ChronoUnit.DAYS);
            subscription.setEndsAt(ends);
            subscription.setNextBillingAt(ends);
        }

        subscription.setAutoRenew(false);

        return repositorySubscription.save(subscription);
    }
}
