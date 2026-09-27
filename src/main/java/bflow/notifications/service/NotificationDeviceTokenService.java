package bflow.notifications.service;

import bflow.notifications.DTO.RegisterDeviceRequest;
import bflow.notifications.entity.NotificationDeviceToken;
import bflow.notifications.repository.NotificationDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Registers and revokes push tokens for authenticated users. */
@Service
@RequiredArgsConstructor
public class NotificationDeviceTokenService {

    /** Repository for device-token records. */
    private final NotificationDeviceTokenRepository repository;

    /**
     * Registers a token, moving it to the current user if the client
     * rotated ownership after logout or account re-authentication.
     *
     * @param userId authenticated user identifier
     * @param request token and platform payload
     */
    @Transactional
    public void register(
            final UUID userId,
            final RegisterDeviceRequest request
    ) {
        NotificationDeviceToken device = repository
                .findByToken(request.token())
                .orElseGet(NotificationDeviceToken::new);

        device.setUserId(userId);
        device.setToken(request.token());
        device.setPlatform(request.platform());
        device.setEnabled(true);

        repository.save(device);
    }

    /**
     * Revokes a token for the authenticated user.
     *
     * @param userId authenticated user identifier
     * @param token FCM registration token
     */
    @Transactional
    public void unregister(final UUID userId, final String token) {
        repository.deleteByTokenAndUserId(token, userId);
    }
}
