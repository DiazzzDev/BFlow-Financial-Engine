package bflow.notifications.service;

import bflow.budget.DTO.BudgetResponse;
import bflow.notifications.DTO.NotificationResponse;
import bflow.notifications.entity.Notification;
import bflow.notifications.enums.NotificationType;
import bflow.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;

    public void sendBudgetWarning(UUID userId, BudgetResponse budget) {
        create(
                userId,
                NotificationType.BUDGET_WARNING,
                "Budget warning",
                "You have used " + budget.getPercentage() + "% of your budget"
        );
    }

    public void sendBudgetCritical(UUID userId, BudgetResponse budget) {
        create(
                userId,
                NotificationType.BUDGET_CRITICAL,
                "Budget critical",
                "You have used " + budget.getPercentage() + "% of your budget"
        );
    }

    public void sendBudgetExceeded(UUID userId, BudgetResponse budget) {
        create(
                userId,
                NotificationType.BUDGET_EXCEEDED,
                "Budget exceeded",
                "You exceeded your budget"
        );
    }

    private void create(
            UUID userId,
            NotificationType type,
            String title,
            String message
    ) {
        Notification notification = new Notification();

        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);

        repository.save(notification);
    }

    public List<NotificationResponse> getUserNotifications(UUID userId) {

        return repository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Long getUnreadCount(UUID userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    public void markAsRead(UUID notificationId, UUID userId) {

        Notification notification = repository.findById(notificationId)
                .orElseThrow();

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        notification.setRead(true);
        repository.save(notification);
    }

    private NotificationResponse toResponse(Notification n) {

        NotificationResponse r = new NotificationResponse();

        r.setId(n.getId());
        r.setTitle(n.getTitle());
        r.setMessage(n.getMessage());
        r.setType(n.getType().name());
        r.setRead(n.getRead());
        r.setCreatedAt(n.getCreatedAt());

        return r;
    }
}