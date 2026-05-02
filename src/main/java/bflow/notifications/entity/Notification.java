package bflow.notifications.entity;

import bflow.notifications.enums.NotificationType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification entity class.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public final class Notification {

    /**
     * The notification ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user ID associated with this notification.
     */
    private UUID userId;

    /**
     * The notification type.
     */
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    /**
     * The notification title.
     */
    private String title;

    /**
     * The notification message.
     */
    private String message;

    /**
     * Whether the notification has been read.
     */
    private Boolean read = false;

    /**
     * The creation timestamp.
     */
    private Instant createdAt;
}
