package bflow.notifications.service;

import bflow.notifications.enums.NotificationType;

import java.util.UUID;

/** Event published after an in-app notification is persisted. */
public record NotificationCreatedEvent(
        UUID notificationId,
        UUID userId,
        NotificationType type,
        String title,
        String message
) { }
