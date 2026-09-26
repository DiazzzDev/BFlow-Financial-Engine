package bflow.notifications.repository;

import bflow.notifications.entity.NotificationDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations for registered push-notification tokens. */
public interface NotificationDeviceTokenRepository
        extends JpaRepository<NotificationDeviceToken, UUID> {

    /**
     * Finds a token regardless of its current owner or enabled state.
     *
     * @param token FCM registration token
     * @return matching token record, if present
     */
    Optional<NotificationDeviceToken> findByToken(String token);

    /**
     * Finds enabled tokens for a user.
     *
     * @param userId user identifier
     * @return enabled tokens
     */
    List<NotificationDeviceToken> findByUserIdAndEnabledTrue(UUID userId);

    /**
     * Removes a token only when it belongs to the authenticated user.
     *
     * @param token FCM registration token
     * @param userId authenticated user identifier
     */
    void deleteByTokenAndUserId(String token, UUID userId);
}
