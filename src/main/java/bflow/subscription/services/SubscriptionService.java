package bflow.subscription.services;

import bflow.auth.entities.User;
import bflow.common.i18n.MessageService;
import bflow.subscription.WompiApiClient;
import bflow.subscription.dto.SubscriptionResponse;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** Number of days in a year, used for annual subscription timing. */
    private static final int DAYS_PER_YEAR = 365;

    /** Number of days in a month, used for monthly subscription timing. */
    private static final int DAYS_PER_MONTH = 30;

    /** Repository used to access subscription data. */
    private final RepositorySubscription repositorySubscription;

    /** Service that resolves plan information. */
    private final PlanService planService;

    /** Client used to deactivate recurring Wompi links. */
    private final WompiApiClient wompiApiClient;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Retrieve the subscriptions owned by the given user.
     *
     * @param userId the user identifier
     * @return the current user's subscriptions as response DTOs
     */
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> findMySubscriptions(final UUID userId) {
        return repositorySubscription
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    /**
     * Cancel an existing subscription if the user is allowed to do so.
     *
     * @param userId the current user identifier
     * @param subscriptionId the subscription identifier to cancel
     */
    @Transactional
    public void cancel(final UUID userId, final UUID subscriptionId) {
        Subscription subscription = repositorySubscription
                .findById(subscriptionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        messageService.get("subscription.notFound")
                ));

        if (!subscription.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(
                    messageService.get("subscription.cancel.notAuthorized")
            );
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return;
        }

        if (subscription.getPlan().getCode().equals("FREE")) {
            throw new IllegalStateException(
                    messageService.get("subscription.freePlan.cannotCancel")
            );
        }

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException(
                    messageService.get(
                            "subscription.cancel.invalidStatus",
                            subscription.getStatus()
                    )
            );
        }

        if (subscription.getProviderLinkId() != null) {
            try {
                wompiApiClient.deactivateRecurringLink(
                        subscription.getProviderLinkId()
                );
                log.info(
                        "Enlace recurrente {} desactivado en Wompi para "
                                + "subscription {}",
                        subscription.getProviderLinkId(),
                        subscription.getId()
                );
            } catch (Exception e) {
                log.error(
                        "No se pudo desactivar el enlace {} en Wompi para "
                                + "subscription {}. Requiere desactivación "
                                + "manual en el panel para evitar "
                                + "cobros futuros.",
                        subscription.getProviderLinkId(),
                        subscription.getId(),
                        e
                );
            }
        }

        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(Instant.now());
        subscription.setAutoRenew(false);
        subscription.setEndsAt(Instant.now());
        subscription.setNextBillingAt(null);
        repositorySubscription.save(subscription);
    }

    /**
     * Create a free subscription for a newly registered user.
     *
     * @param user the user entity
     */
    @Transactional
    public void createFreeSubscription(final User user) {
        boolean hasActiveSubscription = repositorySubscription
                .findByUserIdAndStatus(user.getId(), SubscriptionStatus.ACTIVE)
                .isPresent();

        if (hasActiveSubscription) {
            return;
        }

        Plan freePlan = planService.getFreePlan();

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingAmount(freePlan.getPrice());
        subscription.setStartsAt(Instant.now());
        subscription.setAutoRenew(false);

        try {
            repositorySubscription.save(subscription);
        } catch (DataIntegrityViolationException ex) {
            log.debug(
                    "Concurrent bootstrap detected for user {}, "
                            + "FREE plan creation will be omitted.",
                    user.getId()
            );
        }
    }
}

