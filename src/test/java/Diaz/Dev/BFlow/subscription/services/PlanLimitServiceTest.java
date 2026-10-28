package Diaz.Dev.BFlow.subscription.services;

import bflow.common.exception.PlanLimitExceededException;
import bflow.common.i18n.MessageService;
import bflow.subscription.entities.Feature;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositoryPlanFeature;
import bflow.subscription.repository.RepositorySubscription;
import bflow.subscription.services.PlanLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    @Mock private RepositorySubscription repositorySubscription;
    @Mock private RepositoryPlanFeature repositoryPlanFeature;
    @Mock private MessageService messageService;
    @InjectMocks private PlanLimitService service;

    @Test
    void assertCanCreate_allowsAnUnlimitedFeatureForActiveSubscription() {
        UUID userId = UUID.randomUUID();
        PlanFeature feature = feature(null, true);
        stubActiveSubscription(userId, feature);

        assertDoesNotThrow(() -> service.assertCanCreate(userId, "WALLETS", 99));
    }

    @Test
    void assertCanCreate_rejectsWhenThePlanLimitIsReached() {
        UUID userId = UUID.randomUUID();
        PlanFeature feature = feature(2, true);
        stubActiveSubscription(userId, feature);
        when(messageService.get(any(), any(), any())).thenReturn("limit reached");

        assertThrows(PlanLimitExceededException.class,
                () -> service.assertCanCreate(userId, "WALLETS", 2));
    }

    @Test
    void assertFeatureEnabled_rejectsDisabledFeatureForPastDueSubscription() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = subscription();
        PlanFeature feature = feature(null, false);
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.PAST_DUE))
                .thenReturn(Optional.of(subscription));
        when(repositoryPlanFeature.findByPlanIdAndFeatureCode(
                subscription.getPlan().getId(), "BUDGETS"))
                .thenReturn(Optional.of(feature));
        when(messageService.get(any(), any())).thenReturn("not included");

        assertThrows(PlanLimitExceededException.class,
                () -> service.assertFeatureEnabled(userId, "BUDGETS"));
    }

    @Test
    void assertCanCreate_rejectsUserWithoutActiveOrPastDueSubscription() {
        UUID userId = UUID.randomUUID();
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.PAST_DUE)).thenReturn(Optional.empty());
        when(messageService.get("subscription.noActiveSubscription"))
                .thenReturn("no subscription");

        assertThrows(IllegalStateException.class,
                () -> service.assertCanCreate(userId, "WALLETS", 0));
        verify(repositoryPlanFeature, never()).findByPlanIdAndFeatureCode(
                any(), eq("WALLETS"));
    }

    private void stubActiveSubscription(
            final UUID userId, final PlanFeature feature
    ) {
        Subscription subscription = subscription();
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(repositoryPlanFeature.findByPlanIdAndFeatureCode(
                subscription.getPlan().getId(), "WALLETS"))
                .thenReturn(Optional.of(feature));
    }

    private Subscription subscription() {
        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setCode("FREE");
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        return subscription;
    }

    private PlanFeature feature(final Integer limit, final boolean enabled) {
        Feature definition = new Feature();
        definition.setName("Wallets");
        PlanFeature feature = new PlanFeature();
        feature.setFeature(definition);
        feature.setLimit(limit);
        feature.setEnabled(enabled);
        return feature;
    }
}
