package bflow.notifications.service;

import bflow.notifications.entity.NotificationDeviceToken;
import bflow.notifications.repository.NotificationDeviceTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Sends push notifications through Firebase Cloud Messaging. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    /** Optional FCM client; absent when Firebase is disabled locally. */
    private final ObjectProvider<FirebaseMessaging> firebaseMessaging;

    /** Repository for active device tokens. */
    private final NotificationDeviceTokenRepository tokenRepository;

    /**
     * Sends a notification to every active device registered by a user.
     * Delivery failures are isolated per token and never propagate to the
     * transaction that created the in-app notification.
     *
     * @param userId notification recipient
     * @param notificationId persisted in-app notification identifier
     * @param type notification type
     * @param title notification title
     * @param body notification body
     */
    public void send(
            final UUID userId,
            final UUID notificationId,
            final String type,
            final String title,
            final String body
    ) {
        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
        if (messaging == null) {
            return;
        }

        List<NotificationDeviceToken> devices = tokenRepository
                .findByUserIdAndEnabledTrue(userId);

        for (NotificationDeviceToken device : devices) {
            Message message = Message.builder()
                    .setToken(device.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("notificationId", notificationId.toString())
                    .putData("type", type)
                    .putData("title", title)
                    .putData("body", body)
                    .build();

            try {
                messaging.send(message);
            } catch (FirebaseMessagingException ex) {
                if (isUnregistered(ex)) {
                    device.setEnabled(false);
                    tokenRepository.save(device);
                }
                log.warn(
                        "FCM delivery failed for user {} and token {} "
                                + "(code={}): {}",
                        userId, device.getId(), ex.getMessagingErrorCode(),
                        ex.getMessage()
                );
            } catch (RuntimeException ex) {
                log.warn(
                        "FCM delivery failed for user {} and token {}: {}",
                        userId, device.getId(), ex.getMessage()
                );
            }
        }
    }

    private boolean isUnregistered(final FirebaseMessagingException ex) {
        String message = ex.getMessage();
        return ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                || (message != null
                && message.toLowerCase().contains("notregistered"));
    }
}
