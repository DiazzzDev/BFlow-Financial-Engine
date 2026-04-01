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
public final class NotificationService {
    /**
     * Repository for notification operations.
     */
    private final NotificationRepository repository;

    /**
     * Send a warning notification about budget usage.
     *
     * @param userId the user ID
     * @param budget the budget response
     */
    public void sendBudgetWarning(final UUID userId,
            final BudgetResponse budget) {
        create(
                userId,
                NotificationType.BUDGET_WARNING,
                "Budget warning",
                "You have used " + budget.getPercentage() + "% of your budget"
        );
    }

    /**
     * Send a critical notification about budget usage.
     *
     * @param userId the user ID
     * @param budget the budget response
     */
    public void sendBudgetCritical(final UUID userId,
            final BudgetResponse budget) {
        create(
                userId,
                NotificationType.BUDGET_CRITICAL,
                "Budget critical",
                "You have used " + budget.getPercentage() + "% of your budget"
        );
    }

    /**
     * Send a notification about exceeded budget.
     *
     * @param userId the user ID
     * @param budget the budget response
     */
    public void sendBudgetExceeded(final UUID userId,
            final BudgetResponse budget) {
        create(
                userId,
                NotificationType.BUDGET_EXCEEDED,
                "Budget exceeded",
                "You exceeded your budget"
        );
    }

    /**
     * Create and save a notification.
     *
     * @param userId the user ID
     * @param type the notification type
     * @param title the notification title
     * @param message the notification message
     */
    private void create(
            final UUID userId,
            final NotificationType type,
            final String title,
            final String message
    ) {
        Notification notification = new Notification();

        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);

        repository.save(notification);
    }

    /**
     * Get all notifications for a user.
     *
     * @param userId the user ID
     * @return list of notification responses
     */
    public List<NotificationResponse> getUserNotifications(final UUID userId) {

        return repository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get the count of unread notifications for a user.
     *
     * @param userId the user ID
     * @return count of unread notifications
     */
    public Long getUnreadCount(final UUID userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Mark a notification as read.
     *
     * @param notificationId the notification ID
     * @param userId the user ID
     */
    public void markAsRead(final UUID notificationId,
            final UUID userId) {

        Notification notification = repository.findById(notificationId)
                .orElseThrow();

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        notification.setRead(true);
        repository.save(notification);
    }

    /**
     * Send a success notification about completing a budget period.
     *
     * @param userId the user ID
     * @param budget the budget response
     */
    public void sendBudgetSuccess(
            final UUID userId,
            final BudgetResponse budget
    ) {
        create(
                userId,
                NotificationType.BUDGET_SUCCESS,
                "Budget Completed!",
                "You successfully stayed within your budget"
        );

        //emailService.sendBudgetSuccessEmail(userId, budget);
    }

    /**
     * Convert a notification entity to a response DTO.
     *
     * @param n the notification entity
     * @return the notification response
     */
    private NotificationResponse toResponse(final Notification n) {

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
