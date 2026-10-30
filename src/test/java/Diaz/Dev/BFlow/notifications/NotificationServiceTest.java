package Diaz.Dev.BFlow.notifications;

import bflow.auth.entities.User;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.repository.RepositoryUser;
import bflow.budget.DTO.BudgetResponse;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.aws.service.SesEmailService;
import bflow.common.exception.EmailDeliveryException;
import bflow.common.i18n.MessageService;
import bflow.notifications.DTO.NotificationResponse;
import bflow.notifications.entity.Notification;
import bflow.notifications.enums.NotificationType;
import bflow.notifications.repository.NotificationRepository;
import bflow.notifications.service.NotificationCreatedEvent;
import bflow.notifications.service.NotificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService#sendBudgetGroupSuccess},
 * which fans a single "budget stayed on track" event out to every
 * member of a shared wallet as both an in-app notification and a
 * Thymeleaf-templated email.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SesEmailService emailService;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private MessageService messageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                emailService,
                repositoryUser,
                emailTemplateService,
                messageService,
                eventPublisher
        );

        // Shared stub — not every test in this class triggers a
        // save (the empty-member-list test doesn't), so it's
        // lenient rather than strict to avoid a false-positive
        // UnnecessaryStubbingException there.
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(final String email, final String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setName(name);
        return u;
    }

    @Test
    void sendBudgetGroupSuccess_notifiesAndEmailsEveryMember() {
        User alice = user("alice@example.com", "Alice");
        User bob = user("bob@example.com", "Bob");
        BudgetResponse response = new BudgetResponse();

        notificationService.sendBudgetGroupSuccess(
                List.of(alice, bob), "Household", response);

        ArgumentCaptor<Notification> notifCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2))
                .save(notifCaptor.capture());

        List<Notification> saved = notifCaptor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals(NotificationType.BUDGET_GROUP_SUCCESS,
                saved.get(0).getType());
        assertEquals(alice.getId(), saved.get(0).getUserId());
        assertEquals(bob.getId(), saved.get(1).getUserId());

        // Each member gets their own Thymeleaf-templated email,
        // addressed by name, not a single blast email.
        verify(emailTemplateService).sendBudgetGroupSuccessEmail(
                eq("alice@example.com"), eq("Alice"),
                eq("Household"), eq(response), eq(SupportedLanguage.ES));
        verify(emailTemplateService).sendBudgetGroupSuccessEmail(
                eq("bob@example.com"), eq("Bob"),
                eq("Household"), eq(response), eq(SupportedLanguage.ES));

        // This flow must go through the templated path, never the
        // legacy plain-text sendEmail used by the personal flow.
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void subscriptionRenewalReminderCreatesLocalizedInAppNotification() {
        User user = user("member@example.com", "Member");
        user.setLanguage(SupportedLanguage.ES);
        when(messageService.getForLanguage(
                "notification.subscriptionRenewal.title",
                SupportedLanguage.ES)).thenReturn("Próximo cobro");
        when(messageService.getForLanguage(
                "notification.subscriptionRenewal.message",
                SupportedLanguage.ES, "Pro", "2026-11-28"))
                .thenReturn("El plan Pro se renueva el 2026-11-28");

        notificationService.sendSubscriptionRenewalReminder(
                user, "Pro", "2026-11-28");

        ArgumentCaptor<Notification> notification =
                ArgumentCaptor.forClass(Notification.class);
        ArgumentCaptor<Object> publishedEvent =
                ArgumentCaptor.forClass(Object.class);
        verify(notificationRepository).save(notification.capture());
        assertEquals(NotificationType.SUBSCRIPTION_RENEWAL,
                notification.getValue().getType());
        assertEquals(user.getId(), notification.getValue().getUserId());
        assertEquals("Próximo cobro", notification.getValue().getTitle());
        assertEquals("El plan Pro se renueva el 2026-11-28",
                notification.getValue().getMessage());
        verify(eventPublisher).publishEvent(publishedEvent.capture());
        assertEquals(new NotificationCreatedEvent(
                null,
                user.getId(),
                NotificationType.SUBSCRIPTION_RENEWAL,
                "Próximo cobro",
                "El plan Pro se renueva el 2026-11-28"),
                publishedEvent.getValue());
    }

    @Test
    void sendBudgetGroupSuccess_emptyMemberList_sendsNothing() {
        BudgetResponse response = new BudgetResponse();

        notificationService.sendBudgetGroupSuccess(
                List.of(), "Household", response);

        verify(notificationRepository, never()).save(any());
        verify(emailTemplateService, never())
                .sendBudgetGroupSuccessEmail(any(), any(), any(), any(), any());
    }

    @Test
    void sendWalletInvitation_createsLocalizedActionableNotification() {
        User invitedUser = user("member@example.com", "Member");
        invitedUser.setLanguage(SupportedLanguage.EN);
        UUID invitationId = UUID.randomUUID();

        when(messageService.getForLanguage(
                "notification.walletInvitation.title", SupportedLanguage.EN))
                .thenReturn("Wallet invitation");
        when(messageService.getForLanguage(
                "notification.walletInvitation.message", SupportedLanguage.EN,
                "Alice", "Shared"))
                .thenReturn("Alice invited you to collaborate in \"Shared\".");

        notificationService.sendWalletInvitation(
                invitedUser, invitationId, "Alice", "Shared");

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());

        Notification saved = notificationCaptor.getValue();
        assertEquals(invitedUser.getId(), saved.getUserId());
        assertEquals(NotificationType.WALLET_INVITATION, saved.getType());
        assertEquals(invitationId, saved.getReferenceId());
        assertEquals("Wallet invitation", saved.getTitle());
        assertEquals("Alice invited you to collaborate in \"Shared\".",
                saved.getMessage());
    }

    @Test
    void sendBudgetSuccessKeepsInAppNotificationWhenSesIsUnavailable() {
        User owner = user("owner@example.com", "Owner");
        when(repositoryUser.findById(owner.getId())).thenReturn(java.util.Optional.of(owner));
        org.mockito.Mockito.doThrow(new EmailDeliveryException("SES unavailable"))
                .when(emailService).sendEmail(any(), any(), any());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                notificationService.sendBudgetSuccess(owner.getId(), new BudgetResponse()));

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendEmail(owner.getEmail(), "Budget Completed",
                "You successfully stayed within your budget");
    }

    @Test
    void groupEmailFailureDoesNotStopNotificationsForOtherMembers() {
        User alice = user("alice@example.com", "Alice");
        User bob = user("bob@example.com", "Bob");
        org.mockito.Mockito.doThrow(new EmailDeliveryException("SES unavailable"))
                .when(emailTemplateService).sendBudgetGroupSuccessEmail(
                        eq(alice.getEmail()), eq(alice.getName()),
                        eq("Household"), any(BudgetResponse.class), any());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                notificationService.sendBudgetGroupSuccess(
                        List.of(alice, bob), "Household", new BudgetResponse()));

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(emailTemplateService, times(2)).sendBudgetGroupSuccessEmail(
                any(), any(), eq("Household"), any(), any());
    }

    @Test
    void budgetThresholdNotificationsShowAccurateUsageAndEmailSubjects() {
        User owner = user("owner@example.com", "Owner");
        when(repositoryUser.findById(owner.getId()))
                .thenReturn(Optional.of(owner));
        BudgetResponse budget = new BudgetResponse();
        budget.setPercentage(75);

        notificationService.sendBudgetWarning(owner.getId(), budget);
        notificationService.sendBudgetCritical(owner.getId(), budget);
        notificationService.sendBudgetExceeded(owner.getId(), budget);

        ArgumentCaptor<Notification> notifications =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(notifications.capture());
        assertEquals(List.of(
                        NotificationType.BUDGET_WARNING,
                        NotificationType.BUDGET_CRITICAL,
                        NotificationType.BUDGET_EXCEEDED),
                notifications.getAllValues().stream()
                        .map(Notification::getType).toList());
        assertEquals(List.of(
                        "Budget warning",
                        "Budget critical",
                        "Budget exceeded"),
                notifications.getAllValues().stream()
                        .map(Notification::getTitle).toList());
        assertEquals(List.of(
                        "You have used 75% of your budget",
                        "You have used 75% of your budget",
                        "You exceeded your budget"),
                notifications.getAllValues().stream()
                        .map(Notification::getMessage).toList());

        verify(emailService).sendEmail(owner.getEmail(), "Budget Warning",
                "You have used 75% of your budget");
        verify(emailService).sendEmail(owner.getEmail(), "Budget Critical",
                "You have used 75% of your budget");
        verify(emailService).sendEmail(owner.getEmail(), "Budget Exceeded",
                "You exceeded your budget");
    }

    @Test
    void budgetNotificationStillAppearsWhenRecipientHasNoEmailAccount() {
        UUID userId = UUID.randomUUID();
        when(repositoryUser.findById(userId)).thenReturn(Optional.empty());
        BudgetResponse budget = new BudgetResponse();
        budget.setPercentage(50);

        notificationService.sendBudgetWarning(userId, budget);

        ArgumentCaptor<Notification> notification =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertEquals(userId, notification.getValue().getUserId());
        assertEquals(NotificationType.BUDGET_WARNING,
                notification.getValue().getType());
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void notificationInboxMapsSavedFieldsAndUnreadCount() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setUserId(userId);
        notification.setType(NotificationType.WALLET_INVITATION);
        notification.setTitle("Wallet invitation");
        notification.setMessage("Alice invited you");
        notification.setRead(false);
        notification.setCreatedAt(Instant.parse("2026-10-29T12:00:00Z"));
        notification.setReferenceId(referenceId);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(notification));
        when(notificationRepository.countByUserIdAndReadFalse(userId))
                .thenReturn(1L);

        List<NotificationResponse> inbox =
                notificationService.getUserNotifications(userId);

        assertEquals(1, inbox.size());
        NotificationResponse item = inbox.getFirst();
        assertEquals(notificationId, item.getId());
        assertEquals("Wallet invitation", item.getTitle());
        assertEquals("Alice invited you", item.getMessage());
        assertEquals("WALLET_INVITATION", item.getType());
        assertFalse(item.getRead());
        assertEquals(notification.getCreatedAt(), item.getCreatedAt());
        assertEquals(referenceId, item.getReferenceId());
        assertEquals(1L, notificationService.getUnreadCount(userId));
    }

    @Test
    void markingOwnNotificationAsReadPersistsTheInboxState() {
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setRead(false);
        when(notificationRepository.findById(notification.getId()))
                .thenReturn(Optional.of(notification));

        notificationService.markAsRead(notification.getId(), userId);

        assertTrue(notification.getRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markingAnotherUsersNotificationAsReadIsDeniedWithoutSaving() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(ownerId);
        when(notificationRepository.findById(notification.getId()))
                .thenReturn(Optional.of(notification));
        when(messageService.get("wallet.accessDenied"))
                .thenReturn("You cannot access this notification");

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> notificationService.markAsRead(
                        notification.getId(), requesterId));

        assertEquals("You cannot access this notification",
                exception.getMessage());
        assertFalse(notification.getRead());
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
