package bflow.subscription.scheduler;

import bflow.common.aws.service.EmailTemplateService;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
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

    /** Repository used to access subscriptions. */
    private final RepositorySubscription repositorySubscription;

    /** Service used to reconcile pending activations. */
    private final SubscriptionReconciliationService
            subscriptionReconciliationService;

    /** Service used to send renewal emails. */
    private final EmailTemplateService emailTemplateService;

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

        List<Subscription> dueSoon = repositorySubscription
                .findRenewalReminderCandidates(
                        BillingPeriod.YEARLY,
                        SubscriptionStatus.ACTIVE,
                        now,
                        windowEnd
                );

        for (Subscription subscription : dueSoon) {
            emailTemplateService.sendRenewalReminderEmail(
                    subscription.getUser().getEmail(),
                    subscription.getUser().getName(),
                    subscription.getPlan().getName(),
                    subscription.getBillingAmount().toString(),
                    subscription.getNextBillingAt().toString(),
                    subscription.getCheckoutUrl(),
                    subscription.getUser().getLanguage()
            );

            subscription.setReminderSentAt(now);
            repositorySubscription.save(subscription);
        }
    }

    /**
     * Reconcile pending activations every six hours.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void reconcilePending() {
        subscriptionReconciliationService.reconcilePendingActivations();
    }
}
