package bflow.notifications.entity;

import bflow.notifications.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String title;
    private String message;

    private Boolean read = false;

    private Instant createdAt;
}
