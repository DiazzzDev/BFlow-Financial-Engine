package Diaz.Dev.BFlow.notifications;

import bflow.notifications.entity.NotificationDeviceToken;
import bflow.notifications.repository.NotificationDeviceTokenRepository;
import bflow.notifications.service.FcmPushService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceFallbackTest {

    @Mock private ObjectProvider<FirebaseMessaging> messagingProvider;
    @Mock private NotificationDeviceTokenRepository tokenRepository;
    @Mock private FirebaseMessaging messaging;

    private FcmPushService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FcmPushService(messagingProvider, tokenRepository);
    }

    @Test
    void missingFirebaseClientSkipsPushWithoutQueryingTokens() {
        when(messagingProvider.getIfAvailable()).thenReturn(null);

        service.send(userId, notificationId, "BUDGET_WARNING", "Title", "Body");

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void noActiveDevicesDoesNotAttemptDelivery() throws Exception {
        when(messagingProvider.getIfAvailable()).thenReturn(messaging);
        when(tokenRepository.findByUserIdAndEnabledTrue(userId)).thenReturn(List.of());

        service.send(userId, notificationId, "BUDGET_WARNING", "Title", "Body");

        verify(messaging, never()).send(any(Message.class));
    }

    @Test
    void oneRuntimeDeliveryFailureDoesNotPreventLaterDeviceDelivery() throws Exception {
        when(messagingProvider.getIfAvailable()).thenReturn(messaging);
        when(tokenRepository.findByUserIdAndEnabledTrue(userId))
                .thenReturn(List.of(device("token-1"), device("token-2")));
        when(messaging.send(any(Message.class)))
                .thenThrow(new IllegalStateException("temporary FCM outage"))
                .thenReturn("message-id");

        service.send(userId, notificationId, "BUDGET_WARNING", "Title", "Body");

        verify(messaging, times(2)).send(any(Message.class));
    }

    @Test
    void unregisteredTokenIsDisabledAfterProviderRejectsIt() throws Exception {
        when(messagingProvider.getIfAvailable()).thenReturn(messaging);
        NotificationDeviceToken stale = device("stale-token");
        when(tokenRepository.findByUserIdAndEnabledTrue(userId))
                .thenReturn(List.of(stale));
        FirebaseMessagingException unregistered =
                org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(unregistered.getMessagingErrorCode())
                .thenReturn(MessagingErrorCode.UNREGISTERED);
        when(unregistered.getMessage()).thenReturn("registration no longer exists");
        doThrow(unregistered).when(messaging).send(any(Message.class));

        service.send(userId, notificationId, "BUDGET_WARNING", "Title", "Body");

        org.junit.jupiter.api.Assertions.assertFalse(stale.isEnabled());
        verify(tokenRepository).save(stale);
    }

    @Test
    void temporaryFirebaseMessagingErrorKeepsTokenEnabled() throws Exception {
        when(messagingProvider.getIfAvailable()).thenReturn(messaging);
        NotificationDeviceToken current = device("current-token");
        when(tokenRepository.findByUserIdAndEnabledTrue(userId))
                .thenReturn(List.of(current));
        FirebaseMessagingException temporary =
                org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(temporary.getMessagingErrorCode()).thenReturn(null);
        when(temporary.getMessage()).thenReturn("temporary internal error");
        doThrow(temporary).when(messaging).send(any(Message.class));

        service.send(userId, notificationId, "BUDGET_WARNING", "Title", "Body");

        org.junit.jupiter.api.Assertions.assertTrue(current.isEnabled());
        verify(tokenRepository, never()).save(any(NotificationDeviceToken.class));
    }

    private NotificationDeviceToken device(String token) {
        NotificationDeviceToken device = new NotificationDeviceToken();
        device.setId(UUID.randomUUID());
        device.setUserId(userId);
        device.setToken(token);
        device.setEnabled(true);
        return device;
    }
}
