package bflow.subscription.services;

import bflow.common.exception.PlanLimitExceededException;
import bflow.common.i18n.MessageService;
import bflow.subscription.dto.CurrentSubscriptionResponse;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositoryPlanFeature;
import bflow.subscription.repository.RepositorySubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanLimitService {

    /**
     * Repository for user subscriptions.
     */
    private final RepositorySubscription repositorySubscription;

    /**
     * Repository for plan feature configuration.
     */
    private final RepositoryPlanFeature repositoryPlanFeature;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Verifies that the user can create a new resource according to the
     * current subscription plan.
     *
     * @param userId the user UUID
     * @param featureCode the feature code
     * @param currentCount the current number of created resources
     * @throws PlanLimitExceededException if the feature is disabled or the
     *         plan limit has been reached
     */
    @Transactional(readOnly = true)
    public void assertCanCreate(
        final UUID userId,
        final String featureCode,
        final long currentCount
    ) {
        PlanFeature planFeature = resolvePlanFeature(userId, featureCode);

        if (!planFeature.isEnabled()) {
            throw new PlanLimitExceededException(
                    messageService.get(
                            "subscription.plan.featureNotIncluded",
                            planFeature.getFeature().getName()
                    ));
        }

        Integer limit = planFeature.getLimit();
        if (limit != null && currentCount >= limit) {
            throw new PlanLimitExceededException(
                messageService.get(
                        "subscription.plan.limitReached",
                        limit,
                        planFeature.getFeature().getName()
                )
            );
        }
    }

    /**
     * Verifies that the specified feature is enabled for the user's
     * current subscription plan.
     *
     * @param userId the user UUID
     * @param featureCode the feature code
     * @throws PlanLimitExceededException if the feature is not available
     */
    @Transactional(readOnly = true)
    public void assertFeatureEnabled(
        final UUID userId,
        final String featureCode
    ) {
        PlanFeature planFeature = resolvePlanFeature(userId, featureCode);
        if (!planFeature.isEnabled()) {
            throw new PlanLimitExceededException(
                    messageService.get(
                            "subscription.plan.featureNotIncluded",
                            planFeature.getFeature().getName()
                    ));
        }
    }

    private PlanFeature resolvePlanFeature(
        final UUID userId,
        final String featureCode
    ) {
        Subscription subscription = repositorySubscription
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .or(() -> repositorySubscription.findByUserIdAndStatus(
                        userId, SubscriptionStatus.PAST_DUE
                ))
                .orElseThrow(() -> new IllegalStateException(
                        messageService.get(
                                "subscription.noActiveSubscription"
                        )
                ));

        return repositoryPlanFeature
            .findByPlanIdAndFeatureCode(
                subscription.getPlan().getId(),
                featureCode
            )
            .orElseThrow(() -> new IllegalStateException(
                messageService.get(
                        "subscription.plan.noFeatureConfig",
                        subscription.getPlan().getCode(),
                        featureCode
                )
            ));
    }

    /**
     * Retrieves information about the user's current subscription.
     *
     * @param userId the user UUID
     * @return the current subscription information
     */
    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse getCurrentSubscriptionInfo(
        final UUID userId
    ) {
        Subscription subscription = repositorySubscription
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .or(() -> repositorySubscription.findByUserIdAndStatus(
                        userId, SubscriptionStatus.PAST_DUE
                ))
                .orElseThrow(() -> new IllegalStateException(
                        messageService.get(
                                "subscription.noActiveSubscription"
                        )
                ));

        List<PlanFeature> planFeatures = repositoryPlanFeature
                .findByPlanId(subscription.getPlan().getId());

        Map<String, Boolean> features = planFeatures.stream()
                .collect(Collectors.toMap(
                        pf -> pf.getFeature().getCode(), PlanFeature::isEnabled
                ));

        Map<String, Integer> limits = planFeatures.stream()
                .filter(pf -> pf.getLimit() != null)
                .collect(Collectors.toMap(
                        pf -> pf.getFeature().getCode(), PlanFeature::getLimit
                ));

        return new CurrentSubscriptionResponse(
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                features,
                limits);
    }
}
