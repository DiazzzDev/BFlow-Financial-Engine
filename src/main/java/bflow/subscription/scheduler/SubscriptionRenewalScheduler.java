package bflow.subscription.scheduler;

import bflow.common.aws.service.EmailTemplateService;
import bflow.notifications.service.NotificationService;
import bflow.subscription.dto.CheckoutResponse;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import bflow.subscription.services.PaymentService;
import bflow.subscription.services.SubscriptionReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

    /** Grace period before a monthly subscription is considered overdue. */
    private static final int MONTHLY_GRACE_DAYS = 3;

    /** Reminder window for annual renewals. */
    private static final int ANNUAL_REMINDER_WINDOW_DAYS = 14;

    /** Reminder window before the monthly grace period begins. */
    private static final int MONTHLY_REMINDER_WINDOW_DAYS = 3;

    /** Repository used to access subscriptions. */
    private final RepositorySubscription repositorySubscription;

    /** Service used to reconcile pending activations. */
    private final SubscriptionReconciliationService
            subscriptionReconciliationService;

    /** Service used to send renewal emails. */
    private final EmailTemplateService emailTemplateService;

    /** Service that persists and publishes in-app notifications. */
    private final NotificationService notificationService;

    /** Creates a fresh provider checkout for an annual renewal. */
    private final PaymentService paymentService;

    /**
     * Flag overdue subscriptions when Wompi failed to confirm payment.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void flagOverdueSubscriptions() {
        log.info("flagOverdueSubscriptions: iniciando");

        Instant threshold = Instant.now().minus(
                MONTHLY_GRACE_DAYS,
                ChronoUnit.DAYS
        );

        List<Subscription> overdue = repositorySubscription
                .findAllByStatusAndNextBillingAtBefore(
                        SubscriptionStatus.ACTIVE,
                        threshold
                );

        log.info(
                "flagOverdueSubscriptions: {} suscripciones candidatas",
                overdue.size()
        );

        for (Subscription subscription : overdue) {
            SubscriptionStatus newStatus =
                    subscription.getPlan().getBillingPeriod()
                            == BillingPeriod.YEARLY
                            ? SubscriptionStatus.EXPIRED
                            : SubscriptionStatus.PAST_DUE;

            subscription.setStatus(newStatus);
            repositorySubscription.save(subscription);
            log.warn(
                    "Suscripción {} marcada como {} (venció {})",
                    subscription.getId(),
                    newStatus,
                    subscription.getNextBillingAt()
            );
        }

        log.info("flagOverdueSubscriptions: finalizado");
    }

    /**
     * Send renewal reminders for annual subscriptions that are close to
     * expiring.
     */
    @Scheduled(cron = "0 30 6 * * *")
    @Transactional
    public void sendRenewalReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(
                ANNUAL_REMINDER_WINDOW_DAYS, ChronoUnit.DAYS
        );

        sendRemindersForPeriod(BillingPeriod.YEARLY, now, windowEnd);
        sendRemindersForPeriod(BillingPeriod.MONTHLY, now,
                now.plus(MONTHLY_REMINDER_WINDOW_DAYS, ChronoUnit.DAYS));
    }

    private void sendRemindersForPeriod(
            final BillingPeriod period,
            final Instant now,
            final Instant reminderEnd
    ) {
        List<Subscription> dueSoon = repositorySubscription
                .findRenewalReminderCandidates(period, SubscriptionStatus.ACTIVE,
                        now, reminderEnd);
        for (Subscription subscription : dueSoon) {
            String billingDate = subscription.getNextBillingAt().toString();
            String checkoutUrl = subscription.getCheckoutUrl();
            if (period == BillingPeriod.YEARLY) {
                CheckoutResponse checkout = paymentService.createRenewalCheckout(
                        subscription.getUser().getId(),
                        subscription.getPlan().getId()
                );
                checkoutUrl = checkout.checkoutUrl();
            }
            emailTemplateService.sendRenewalReminderEmail(
                    subscription.getUser().getEmail(),
                    subscription.getUser().getName(),
                    subscription.getPlan().getName(),
                    subscription.getBillingAmount().toString(),
                    billingDate,
                    checkoutUrl,
                    subscription.getUser().getLanguage()
            );
            notificationService.sendSubscriptionRenewalReminder(
                    subscription.getUser(), subscription.getPlan().getName(),
                    billingDate);

            subscription.setReminderSentAt(now);
            repositorySubscription.save(subscription);
        }
    }

    /**
     * Reconcile pending enrolments shortly after Wompi confirms the card.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void reconcilePending() {
        subscriptionReconciliationService.reconcilePendingActivations();
    }
}
