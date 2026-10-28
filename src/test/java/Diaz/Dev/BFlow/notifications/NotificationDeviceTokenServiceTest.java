package Diaz.Dev.BFlow.notifications;

import bflow.notifications.DTO.RegisterDeviceRequest;
import bflow.notifications.entity.NotificationDeviceToken;
import bflow.notifications.enums.DevicePlatform;
import bflow.notifications.repository.NotificationDeviceTokenRepository;
import bflow.notifications.service.NotificationDeviceTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceTokenServiceTest {

    @Mock
    private NotificationDeviceTokenRepository repository;

    private NotificationDeviceTokenService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDeviceTokenService(repository);
    }

    @Test
    void registerCreatesEnabledTokenForCurrentUser() {
        UUID owner = UUID.randomUUID();
        RegisterDeviceRequest request = new RegisterDeviceRequest(
                "fcm-token", DevicePlatform.ANDROID);
        when(repository.findByToken(request.token()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationDeviceToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(owner, request);

        ArgumentCaptor<NotificationDeviceToken> captor =
                ArgumentCaptor.forClass(NotificationDeviceToken.class);
        verify(repository).save(captor.capture());
        NotificationDeviceToken saved = captor.getValue();
        assertEquals(owner, saved.getUserId());
        assertEquals(request.token(), saved.getToken());
        assertEquals(request.platform(), saved.getPlatform());
        assertTrue(saved.isEnabled());
    }

    @Test
    void registerReassignsAndReenablesRotatedToken() {
        UUID oldOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        RegisterDeviceRequest request = new RegisterDeviceRequest(
                "rotated-token", DevicePlatform.IOS);
        NotificationDeviceToken existing = new NotificationDeviceToken();
        existing.setUserId(oldOwner);
        existing.setToken(request.token());
        existing.setPlatform(DevicePlatform.ANDROID);
        existing.setEnabled(false);
        when(repository.findByToken(request.token()))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.register(newOwner, request);

        assertEquals(newOwner, existing.getUserId());
        assertEquals(DevicePlatform.IOS, existing.getPlatform());
        assertTrue(existing.isEnabled());
        verify(repository).save(existing);
    }

    @Test
    void unregisterDeletesOnlyTheTokenOwnedByTheCurrentUser() {
        UUID owner = UUID.randomUUID();

        service.unregister(owner, "fcm-token");

        verify(repository).deleteByTokenAndUserId("fcm-token", owner);
    }
}
