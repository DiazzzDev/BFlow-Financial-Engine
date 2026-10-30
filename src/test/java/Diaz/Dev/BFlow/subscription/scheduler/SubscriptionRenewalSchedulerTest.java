package Diaz.Dev.BFlow.subscription.scheduler;

import bflow.auth.entities.User;
import bflow.auth.enums.SupportedLanguage;
import bflow.common.aws.service.EmailTemplateService;
import bflow.notifications.service.NotificationService;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import bflow.subscription.scheduler.SubscriptionRenewalScheduler;
import bflow.subscription.services.SubscriptionReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRenewalSchedulerTest {

    @Mock private RepositorySubscription repositorySubscription;
    @Mock private SubscriptionReconciliationService reconciliationService;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private NotificationService notificationService;
    @InjectMocks private SubscriptionRenewalScheduler scheduler;

    @Test
    void flagOverdueSubscriptions_marksMonthlyPastDueAndYearlyExpired() {
        Subscription monthly = subscription(BillingPeriod.MONTHLY);
        Subscription yearly = subscription(BillingPeriod.YEARLY);
        when(repositorySubscription.findAllByStatusAndNextBillingAtBefore(
                eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of(monthly, yearly));

        scheduler.flagOverdueSubscriptions();

        assertEquals(SubscriptionStatus.PAST_DUE, monthly.getStatus());
        assertEquals(SubscriptionStatus.EXPIRED, yearly.getStatus());
        verify(repositorySubscription).save(monthly);
        verify(repositorySubscription).save(yearly);
        org.junit.jupiter.api.Assertions.assertNotNull(
                yearly.getReminderSentAt());
    }

    @Test
    void sendRenewalReminders_sendsOneEmailAndMarksSubscription() {
        Subscription yearly = subscription(BillingPeriod.YEARLY);
        when(repositorySubscription.findRenewalReminderCandidates(
                eq(BillingPeriod.YEARLY), eq(SubscriptionStatus.ACTIVE),
                any(), any())).thenReturn(List.of(yearly));

        scheduler.sendRenewalReminders();

        verify(emailTemplateService).sendRenewalReminderEmail(
                yearly.getUser().getEmail(), yearly.getUser().getName(),
                yearly.getPlan().getName(), "10.00",
                yearly.getNextBillingAt().toString(), yearly.getCheckoutUrl(),
                yearly.getUser().getLanguage());
        verify(notificationService).sendSubscriptionRenewalReminder(
                yearly.getUser(), yearly.getPlan().getName(),
                yearly.getNextBillingAt().toString());
        verify(repositorySubscription).save(yearly);
    }

    @Test
    void sendRenewalRemindersAlsoCoversMonthlySubscriptionsBeforeGracePeriod() {
        Subscription monthly = subscription(BillingPeriod.MONTHLY);
        when(repositorySubscription.findRenewalReminderCandidates(
                eq(BillingPeriod.MONTHLY), eq(SubscriptionStatus.ACTIVE),
                any(), any())).thenReturn(List.of(monthly));

        scheduler.sendRenewalReminders();

        verify(emailTemplateService).sendRenewalReminderEmail(
                monthly.getUser().getEmail(), monthly.getUser().getName(),
                monthly.getPlan().getName(), "10.00",
                monthly.getNextBillingAt().toString(), monthly.getCheckoutUrl(),
                monthly.getUser().getLanguage());
        verify(notificationService).sendSubscriptionRenewalReminder(
                monthly.getUser(), monthly.getPlan().getName(),
                monthly.getNextBillingAt().toString());
        verify(repositorySubscription).save(monthly);
        org.junit.jupiter.api.Assertions.assertNotNull(
                monthly.getReminderSentAt());
    }

    @Test
    void remindersDoNotSendOrPersistWhenNoSubscriptionIsDue() {
        scheduler.sendRenewalReminders();

        verify(emailTemplateService, never()).sendRenewalReminderEmail(
                any(), any(), any(), any(), any(), any(), any());
        verify(notificationService, never())
                .sendSubscriptionRenewalReminder(any(), any(), any());
        verify(repositorySubscription, never()).save(any());
    }

    @Test
    void reminderWindowsAreFourteenDaysYearlyAndThreeDaysMonthly() {
        scheduler.sendRenewalReminders();

        org.mockito.ArgumentCaptor<Instant> from =
                org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.ArgumentCaptor<Instant> to =
                org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repositorySubscription).findRenewalReminderCandidates(
                eq(BillingPeriod.YEARLY), eq(SubscriptionStatus.ACTIVE),
                from.capture(), to.capture());
        assertEquals(14L, java.time.Duration.between(
                from.getValue(), to.getValue()).toDays());
        verify(repositorySubscription).findRenewalReminderCandidates(
                eq(BillingPeriod.MONTHLY), eq(SubscriptionStatus.ACTIVE),
                from.capture(), to.capture());
        assertEquals(3L, java.time.Duration.between(
                from.getValue(), to.getValue()).toDays());
    }

    @Test
    void reconcilePending_delegatesToReconciliationService() {
        scheduler.reconcilePending();

        verify(reconciliationService).reconcilePendingActivations();
    }

    private Subscription subscription(final BillingPeriod billingPeriod) {
        User user = new User();
        user.setEmail("member@example.com");
        user.setName("Member");
        user.setLanguage(SupportedLanguage.ES);

        Plan plan = new Plan();
        plan.setName("Pro");
        plan.setBillingPeriod(billingPeriod);

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingAmount(new BigDecimal("10.00"));
        subscription.setNextBillingAt(Instant.now().plusSeconds(60));
        subscription.setCheckoutUrl("https://checkout.example.com");
        return subscription;
    }
}
