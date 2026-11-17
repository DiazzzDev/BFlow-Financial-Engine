package Diaz.Dev.BFlow.subscription.services;

import bflow.auth.entities.User;
import bflow.subscription.WompiApiClient;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import bflow.subscription.services.SubscriptionReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class SubscriptionReconciliationServiceTest {

    @Mock private RepositorySubscription repositorySubscription;
    @Mock private WompiApiClient wompiApiClient;
    private SubscriptionReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionReconciliationService(
                repositorySubscription, wompiApiClient);
    }

    @Test
    void activeWompiEnrollmentRemainsPendingUntilApprovedPaymentWebhook() {
        Subscription subscription = pendingSubscription(Instant.now());
        WompiApiClient.SubscriberInfo enrollment = subscriber("Activa", 0);
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));
        when(wompiApiClient.getSubscribers("provider-link"))
                .thenReturn(List.of(enrollment));

        service.reconcilePendingActivations();

        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);
        verify(repositorySubscription, never()).save(subscription);
    }

    @Test
    void activeWompiEnrollmentMatchingEmailStillDoesNotGrantPro() {
        Subscription subscription = pendingSubscription(Instant.now());
        WompiApiClient.SubscriberInfo enrollment = new WompiApiClient
                .SubscriberInfo("subscriber-id", Instant.now(), "BFlow Pro",
                new BigDecimal("9.99"), 0, "ACTIVA", "USER@EXAMPLE.TEST",
                "Customer", Instant.now(), 28);
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));
        when(wompiApiClient.getSubscribers("provider-link"))
                .thenReturn(List.of(enrollment));

        service.reconcilePendingActivations();

        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);
        verify(repositorySubscription, never()).save(subscription);
    }

    @Test
    void noEnrollmentDuringCheckoutDayRemainsPending() {
        Subscription subscription = pendingSubscription(todayAtNoon());
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));
        when(wompiApiClient.getSubscribers("provider-link"))
                .thenReturn(List.of());

        service.reconcilePendingActivations();

        verify(repositorySubscription, never()).save(subscription);
    }

    @Test
    void inactiveWompiEnrollmentDuringCheckoutDayRemainsPending() {
        Subscription subscription = pendingSubscription(todayAtNoon());
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));
        when(wompiApiClient.getSubscribers("provider-link"))
                .thenReturn(List.of(subscriber("Pendiente", 0)));

        service.reconcilePendingActivations();

        verify(repositorySubscription, never()).save(subscription);
    }

    @Test
    void noEnrollmentExpiresOnTheDayAfterCheckout() {
        Subscription subscription = pendingSubscription(yesterdayAtNoon());
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));

        service.reconcilePendingActivations();

        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        verify(wompiApiClient).deactivateRecurringLink("provider-link");
        verify(repositorySubscription).save(subscription);
        verify(wompiApiClient, never()).getSubscribers(any());
    }

    @Test
    void pendingCheckoutWithoutProviderLinkIsLeftPending() {
        Subscription subscription = pendingSubscription(Instant.now());
        subscription.setProviderLinkId(null);
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));

        service.reconcilePendingActivations();

        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);
        verify(wompiApiClient, never()).getSubscribers(any());
        verify(repositorySubscription, never()).save(subscription);
    }

    @Test
    void checkoutWithoutProviderLinkExpiresAfterCheckoutDay() {
        Subscription subscription = pendingSubscription(yesterdayAtNoon());
        subscription.setProviderLinkId(null);
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));

        service.reconcilePendingActivations();

        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        verify(repositorySubscription).save(subscription);
        verify(wompiApiClient, never()).getSubscribers(any());
        verify(wompiApiClient, never()).deactivateRecurringLink(any());
    }

    @Test
    void providerFailureDoesNotExpireOrChangePendingCheckout() {
        Subscription subscription = pendingSubscription(todayAtNoon());
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));
        doThrow(new IllegalStateException("Wompi unavailable"))
                .when(wompiApiClient).getSubscribers("provider-link");

        service.reconcilePendingActivations();

        org.assertj.core.api.Assertions.assertThat(subscription.getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);
        verify(repositorySubscription, never()).save(subscription);
    }

    @Test
    void unrelatedWompiSubscribersDoNotActivateCheckout() {
        Subscription subscription = pendingSubscription(Instant.now());
        when(repositorySubscription.findAllByStatusAndCreatedAtBefore(
                eq(SubscriptionStatus.PENDING_ACTIVATION), any()))
                .thenReturn(List.of(subscription));
        when(wompiApiClient.getSubscribers("provider-link"))
                .thenReturn(List.of(new WompiApiClient.SubscriberInfo(
                        "other", Instant.now(), "Other plan", BigDecimal.TEN,
                        0, "Activa", "someone-else@example.test", "Other",
                        Instant.now(), 1)));

        service.reconcilePendingActivations();

        verify(repositorySubscription, never()).save(subscription);
    }

    private Subscription pendingSubscription(final Instant createdAt) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.test");
        Plan plan = new Plan();
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
        subscription.setProviderLinkId("provider-link");
        subscription.setBillingAmount(new BigDecimal("9.99"));
        subscription.setCreatedAt(createdAt);
        return subscription;
    }

    private Instant todayAtNoon() {
        return LocalDate.now(ZoneId.of("America/El_Salvador"))
                .atTime(12, 0)
                .atZone(ZoneId.of("America/El_Salvador"))
                .toInstant();
    }

    private Instant yesterdayAtNoon() {
        return LocalDate.now(ZoneId.of("America/El_Salvador"))
                .minusDays(1)
                .atTime(12, 0)
                .atZone(ZoneId.of("America/El_Salvador"))
                .toInstant();
    }

    private WompiApiClient.SubscriberInfo subscriber(
            final String status,
            final int payments
    ) {
        return new WompiApiClient.SubscriberInfo(
                "subscriber-id", Instant.now(), "BFlow Pro",
                new BigDecimal("9.99"), payments, status, "user@example.test",
                "Customer", Instant.now(), 28);
    }
}
