package bflow.notifications.entity;

import bflow.notifications.enums.DevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A push-notification token registered by an authenticated user. */
@Entity
@Table(
        name = "notification_device_tokens",
        uniqueConstraints = @UniqueConstraint(columnNames = "token")
)
@Getter
@Setter
public class NotificationDeviceToken {

    /** Unique device-token record identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Authenticated owner of the token. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** FCM registration token supplied by the client SDK. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;

    /** Platform associated with the token. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    /** Whether delivery should be attempted for this token. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Time when this token was first registered. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Time when this token was last registered or updated. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
