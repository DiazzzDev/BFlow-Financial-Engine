package bflow.subscription.scheduler;

import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
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

    private static final int MONTHLY_GRACE_DAYS = 3;
    private static final int ANNUAL_REMINDER_WINDOW_DAYS = 14;

    private final RepositorySubscription repositorySubscription;
    // private final EmailService emailService; // conecta tu servicio de correo real aquí

    /** Corre diario: si Wompi debió cobrar y no llegó webhook, marca PAST_DUE. */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void flagOverdueSubscriptions() {
        log.info("flagOverdueSubscriptions: iniciando");

        Instant threshold = Instant.now().minus(
            MONTHLY_GRACE_DAYS, ChronoUnit.DAYS
        );

        List<Subscription> overdue = repositorySubscription
                .findAllByStatusAndNextBillingAtBefore(
                    SubscriptionStatus.ACTIVE, threshold
                );

        log.info("flagOverdueSubscriptions: {} suscripciones candidatas", overdue.size());

        for (Subscription subscription : overdue) {
            SubscriptionStatus newStatus = 
            subscription.getPlan().getBillingPeriod() == BillingPeriod.YEARLY
                    // anual: no hay reintento automático posible
                    ? SubscriptionStatus.EXPIRED  

                    // mensual: pudo ser un fallo puntual del cobro de Wompi
                    : SubscriptionStatus.PAST_DUE; 

            subscription.setStatus(newStatus);
            repositorySubscription.save(subscription);
            log.warn("Suscripción {} marcada como {} (venció {})",
                    subscription.getId(), newStatus, subscription.getNextBillingAt());
        }

        log.info("flagOverdueSubscriptions: finalizado");
    }

    /** Corre diario: manda recordatorio a 
     * suscripciones anuales próximas a vencer. */
    @Scheduled(cron = "0 30 6 * * *")
    @Transactional
    public void sendRenewalReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(ANNUAL_REMINDER_WINDOW_DAYS, ChronoUnit.DAYS);

        List<Subscription> dueSoon = repositorySubscription
            .findAllByPlan_BillingPeriodAndStatusAndNextBillingAtBetweenAndReminderSentAtIsNull(
                BillingPeriod.YEARLY,
                SubscriptionStatus.ACTIVE,
                now,
                windowEnd
            );

        for (Subscription subscription : dueSoon) {
            // emailService.sendRenewalReminder(
            //      subscription.getUser(), 
            //      subscription.getPlan(), 
            //      subscription.getPlan().getCheckoutUrl()
            // );
            log.info(
                "Recordatorio de renovación pendiente de enviar a {} para plan {}",
                subscription.getUser().getEmail(), 
                subscription.getPlan().getName()
            );

            subscription.setReminderSentAt(now);
            repositorySubscription.save(subscription);
        }
    }
}
