package bflow.notifications.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivers push notifications only after the database transaction commits. */
@Component
@RequiredArgsConstructor
public class FcmNotificationListener {

    /** FCM delivery service. */
    private final FcmPushService fcmPushService;

    /**
     * Delivers the event after commit so FCM cannot roll back business data.
     *
     * @param event persisted notification details
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onNotificationCreated(final NotificationCreatedEvent event) {
        fcmPushService.send(
                event.userId(),
                event.notificationId(),
                event.type().name(),
                event.title(),
                event.message()
        );
    }
}
