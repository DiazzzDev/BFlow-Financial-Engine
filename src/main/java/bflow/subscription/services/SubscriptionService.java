package bflow.subscription.services;

import bflow.auth.entities.User;
import bflow.common.exception.NotFoundException;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    /** Days in a year used for subscription calculations. */
    private static final int DAYS_PER_YEAR = 365;

    /** Days in a month used for subscription calculations. */
    private static final int DAYS_PER_MONTH = 30;

    /** Repository used for subscription persistence and lookup. */
    private final RepositorySubscription subscriptionRepository;

    /** Service responsible for plan lookup and plan-related operations. */
    private final PlanService planService;

    /**
     * Get the current plan for a user.
     *
     * @param userId the user identifier
     * @return the user's active plan
     */
    public Plan getCurrentPlan(final UUID userId) {
        return getActiveSubscription(userId)
                .getPlan();
    }

    /**
     * Find the active subscription for a user.
     *
     * @param userId the user identifier
     * @return the active Subscription
     */
    public Subscription getActiveSubscription(final UUID userId) {
        return subscriptionRepository
                .findByUserIdAndStatus(
                        userId,
                        SubscriptionStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new NotFoundException(
                                "Active subscription not found"
                        ));
    }

    /**
     * Create a free subscription for a newly registered user.
     *
     * @param user the user entity
     * @return the created or existing free subscription
     */
    @Transactional
    public Subscription createFreeSubscription(final User user) {

        if (subscriptionRepository.existsByUserId(user.getId())) {
            return subscriptionRepository
                    .findByUserId(user.getId())
                    .orElseThrow();
        }

        Plan freePlan = planService.getFreePlan();

        Subscription subscription = new Subscription();

        subscription.setUser(user);
        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPrice(freePlan.getPrice());

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

        return subscriptionRepository.save(subscription);
    }
}
