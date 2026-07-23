package bflow.subscription.repository;

import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositorySubscription
        extends JpaRepository<Subscription, UUID> {

    /**
     * Check whether a user already has any subscription.
     *
     * @param userId the user identifier
     * @return true when a subscription exists for the user
     */
    boolean existsByUserId(UUID userId);

    /**
     * Find the primary subscription for a user.
     *
     * @param userId the user identifier
     * @return optional subscription for the user
     */
    Optional<Subscription> findByUserId(UUID userId);

    /**
     * Check whether a user has an active subscription for a plan.
     *
     * @param userId the user identifier
     * @param planId the plan identifier
     * @param statuses the allowed statuses
     * @return true when a matching subscription exists
     */
    boolean existsByUserIdAndPlanIdAndStatusIn(
            UUID userId,
            UUID planId,
            List<SubscriptionStatus> statuses
    );

    /**
     * Find all subscriptions for a user ordered from newest to oldest.
     *
     * @param userId the user identifier
     * @return subscriptions sorted by creation time
     */
    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find subscriptions that should be billed before a threshold.
     *
     * @param status the initial subscription status
     * @param threshold the billing deadline threshold
     * @return subscriptions due for billing
     */
    List<Subscription> findAllByStatusAndNextBillingAtBefore(
            SubscriptionStatus status,
            Instant threshold
    );

    /**
     * Find subscriptions that need reminder processing in a billing window.
     *
     * @param billingPeriod the billing cadence
     * @param status the current subscription status
     * @param from the start of the window
     * @param to the end of the window
     * @return matching subscriptions
     */
    @Query("""
            select s from Subscription s
            where s.plan.billingPeriod = :billingPeriod
            and s.status = :status
            and s.nextBillingAt between :from and :to
            and s.reminderSentAt is null
            """)
    List<Subscription> findRenewalReminderCandidates(
            @Param("billingPeriod") BillingPeriod billingPeriod,
            @Param("status") SubscriptionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * Find subscriptions that are older than a threshold.
     *
     * @param status the current subscription status
     * @param threshold the cutoff date
     * @return matching subscriptions
     */
    List<Subscription> findAllByStatusAndCreatedAtBefore(
            SubscriptionStatus status,
            Instant threshold
    );

    /**
     * Find a subscription by the provider subscriber identifier.
     *
     * @param providerSubscriberId the provider subscriber id
     * @return matching subscription
     */
    Optional<Subscription> findByProviderSubscriberId(
            String providerSubscriberId
    );

    /**
     * Find subscriptions by email and billing amount.
     *
     * @param email the user email
     * @param billingAmount the expected billing amount
     * @param status the current subscription status
     * @return matching subscriptions
     */
    List<Subscription> findByUserEmailAndBillingAmountAndStatus(
            String email,
            BigDecimal billingAmount,
            SubscriptionStatus status
    );

    /**
     * Find a subscription by checkout reference and status.
     *
     * @param checkoutReference the checkout reference
     * @param status the current subscription status
     * @return matching subscription
     */
    Optional<Subscription> findByCheckoutReferenceAndStatus(
            String checkoutReference,
            SubscriptionStatus status
    );
}
