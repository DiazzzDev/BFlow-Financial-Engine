package bflow.subscription.repository;

import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Find a subscription for a user.
     *
     * @param userId the user identifier
     * @return optional subscription for the user
     */
    Optional<Subscription> findByUserId(UUID userId);

    List<Subscription> findByPlan_IdAndUser_EmailAndStatusIn(
            UUID planId, String email, List<SubscriptionStatus> statuses);

    boolean existsByUser_IdAndPlan_IdAndStatusIn(
            UUID userId, UUID planId, List<SubscriptionStatus> statuses);

    List<Subscription> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    List<Subscription> 
    findAllByStatusAndNextBillingAtBefore(
        SubscriptionStatus status, Instant threshold
    );

    List<Subscription> 
    findAllByPlan_BillingPeriodAndStatusAndNextBillingAtBetweenAndReminderSentAtIsNull(
            BillingPeriod billingPeriod,
            SubscriptionStatus status,
            Instant from,
            Instant to
        );
    
    List<Subscription> findByProviderLinkIdAndUser_EmailAndStatusIn(
            String providerLinkId,
            String email,
            List<SubscriptionStatus> statuses
        );

    List<Subscription> findAllByStatusAndCreatedAtBefore(
            SubscriptionStatus status, Instant threshold);

    Optional<Subscription> findByProviderSubscriberId(String providerSubscriberId);

    List<Subscription> findByUser_EmailAndBillingAmountAndStatus(
            String email, BigDecimal billingAmount, SubscriptionStatus status);

    Optional<Subscription> findByCheckoutReferenceAndStatus(String checkoutReference, SubscriptionStatus status);
}
