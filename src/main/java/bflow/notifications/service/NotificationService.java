package bflow.notifications.service;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.budget.DTO.BudgetResponse;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.aws.service.SesEmailService;
import bflow.common.i18n.MessageService;
import bflow.notifications.DTO.NotificationResponse;
import bflow.notifications.entity.Notification;
import bflow.notifications.enums.NotificationType;
import bflow.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public final class NotificationService {
    /**
     * Repository for notification operations.
     */
    private final NotificationRepository notificationRepository;

    /**
     * Service for sending emails via AWS SES.
     */
    private final SesEmailService emailService;

    /**
     * Repository for user operations.
     */
    private final RepositoryUser repositoryUser;

    /**
     * Service for sending Thymeleaf-templated emails.
     */
    private final EmailTemplateService emailTemplateService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Send a warning notification about budget usage.
     *
     * @param userId the user ID
     * @param budget the budget response
     */
    public void sendBudgetWarning(
            final UUID userId,
            final BudgetResponse budget
    ) {
        String message =
                "You have used " + budget.getPercentage() + "% of your budget";

        create(
                userId,
                NotificationType.BUDGET_WARNING,
                "Budget warning",
                "You have used " + budget.getPercentage() + "% of your budget"
        );

        sendEmail(userId, "Budget Warning", message);
    }

    /**
     * Send a critical notification about budget usage.
     *
     * @param userId the user ID
     * @param budget the budget response
     */
    public void sendBudgetCritical(
            final UUID userId,
            final BudgetResponse budget
    ) {
        String message =
                "You have used " + budget.getPercentage() + "% of your budget";

        create(
                userId,
                NotificationType.BUDGET_CRITICAL,
                "Budget critical",
                "You have used " + budget.getPercentage() + "% of your budget"
        );

        sendEmail(userId, "Budget Critical", message);
    }

    /**
     * Send a notification about exceeded budget.
     *
     * @param userId the user ID
     * @param budget the budget response
     */
    public void sendBudgetExceeded(
            final UUID userId,
            final BudgetResponse budget
    ) {
        String message = "You exceeded your budget";

        create(
                userId,
                NotificationType.BUDGET_EXCEEDED,
                "Budget exceeded",
                "You exceeded your budget"
        );

        sendEmail(userId, "Budget Exceeded", message);
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

        notificationRepository.save(notification);
    }

    /**
     * Get all notifications for a user.
     *
     * @param userId the user ID
     * @return list of notification responses
     */
    public List<NotificationResponse> getUserNotifications(final UUID userId) {

        return notificationRepository
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
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Mark a notification as read.
     *
     * @param notificationId the notification ID
     * @param userId the user ID
     */
    public void markAsRead(final UUID notificationId,
            final UUID userId) {

        Notification notification = notificationRepository.findById(
                notificationId).orElseThrow();

        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException(
                    messageService.get("wallet.accessDenied")
            );
        }

        notification.setRead(true);
        notificationRepository.save(notification);
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
        String message =
                "You successfully stayed within your budget";

        create(
                userId,
                NotificationType.BUDGET_SUCCESS,
                "Budget Completed!",
                "You successfully stayed within your budget"
        );

        sendEmail(userId, "Budget Completed", message);
    }

    /**
     * Send a group celebration notification to every member of a
     * shared wallet when the wallet stays within budget as a team
     * for the completed period. Each member gets both an in-app
     * notification and a Thymeleaf-templated email addressed to
     * them personally.
     *
     * @param members every member of the wallet (including the
     *         budget's owner)
     * @param walletName the shared wallet's display name
     * @param budget the final budget figures for the period
     */
    public void sendBudgetGroupSuccess(
            final List<User> members,
            final String walletName,
            final BudgetResponse budget
    ) {
        for (User member : members) {
            create(
                    member.getId(),
                    NotificationType.BUDGET_GROUP_SUCCESS,
                    "Team budget completed!",
                    walletName + " stayed within budget this period"
            );

            emailTemplateService.sendBudgetGroupSuccessEmail(
                    member.getEmail(),
                    member.getName(),
                    walletName,
                    budget,
                    member.getLanguage()
            );
        }
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

    private void sendEmail(
            final UUID userId,
            final String subject,
            final String message
    ) {
        repositoryUser.findById(userId)
                .ifPresent(user ->
                        emailService.sendEmail(
                                user.getEmail(),
                                subject,
                                message
                        )
                );
    }
}
