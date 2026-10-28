package Diaz.Dev.BFlow.subscription.services;

import bflow.common.exception.PlanLimitExceededException;
import bflow.common.i18n.MessageService;
import bflow.subscription.dto.CurrentSubscriptionResponse;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void assertCanCreate_allowsCountBelowTheConfiguredLimit() {
        UUID userId = UUID.randomUUID();
        stubActiveSubscription(userId, feature(3, true));

        assertDoesNotThrow(() -> service.assertCanCreate(
                userId, "WALLETS", 2));
    }

    @Test
    void assertCanCreate_rejectsDisabledFeature() {
        UUID userId = UUID.randomUUID();
        PlanFeature feature = feature(null, false);
        stubActiveSubscription(userId, feature);
        when(messageService.get(any(), any())).thenReturn("not included");

        assertThrows(PlanLimitExceededException.class,
                () -> service.assertCanCreate(userId, "WALLETS", 0));
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
    void assertFeatureEnabled_allowsEnabledFeature() {
        UUID userId = UUID.randomUUID();
        stubActiveSubscription(userId, feature(null, true));

        assertDoesNotThrow(() -> service.assertFeatureEnabled(
                userId, "WALLETS"));
    }

    @Test
    void getCurrentSubscriptionInfo_returnsEnabledFeaturesAndFiniteLimits() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = subscription();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        PlanFeature wallets = feature(5, true);
        wallets.getFeature().setCode("WALLETS");
        PlanFeature budgets = feature(null, false);
        budgets.getFeature().setCode("BUDGETS");
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(repositoryPlanFeature.findByPlanId(subscription.getPlan().getId()))
                .thenReturn(List.of(wallets, budgets));

        CurrentSubscriptionResponse response =
                service.getCurrentSubscriptionInfo(userId);

        assertEquals("FREE", response.planCode());
        assertEquals(SubscriptionStatus.ACTIVE, response.status());
        assertEquals(Boolean.TRUE, response.features().get("WALLETS"));
        assertEquals(Boolean.FALSE, response.features().get("BUDGETS"));
        assertEquals(5, response.limits().get("WALLETS"));
        assertTrue(!response.limits().containsKey("BUDGETS"));
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

    @Test
    void assertFeatureEnabled_rejectsUserWithoutAnySubscription() {
        UUID userId = UUID.randomUUID();
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.PAST_DUE)).thenReturn(Optional.empty());
        when(messageService.get("subscription.noActiveSubscription"))
                .thenReturn("no subscription");

        assertThrows(IllegalStateException.class,
                () -> service.assertFeatureEnabled(userId, "WALLETS"));

        verify(repositoryPlanFeature, never()).findByPlanIdAndFeatureCode(
                any(), eq("WALLETS"));
    }

    @Test
    void getCurrentSubscriptionInfo_usesPastDueFallbackWithEmptyPlanFeatures() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = subscription();
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.PAST_DUE))
                .thenReturn(Optional.of(subscription));
        when(repositoryPlanFeature.findByPlanId(subscription.getPlan().getId()))
                .thenReturn(List.of());

        CurrentSubscriptionResponse response =
                service.getCurrentSubscriptionInfo(userId);

        assertEquals(SubscriptionStatus.PAST_DUE, response.status());
        assertTrue(response.features().isEmpty());
        assertTrue(response.limits().isEmpty());
    }

    @Test
    void getCurrentSubscriptionInfo_rejectsUserWithoutAnySubscription() {
        UUID userId = UUID.randomUUID();
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repositorySubscription.findByUserIdAndStatus(
                userId, SubscriptionStatus.PAST_DUE)).thenReturn(Optional.empty());
        when(messageService.get("subscription.noActiveSubscription"))
                .thenReturn("no subscription");

        assertThrows(IllegalStateException.class,
                () -> service.getCurrentSubscriptionInfo(userId));

        verify(repositoryPlanFeature, never()).findByPlanId(any());
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
        definition.setCode("WALLETS");
        definition.setName("Wallets");
        PlanFeature feature = new PlanFeature();
        feature.setFeature(definition);
        feature.setLimit(limit);
        feature.setEnabled(enabled);
        return feature;
    }
}
