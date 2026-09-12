package Diaz.Dev.BFlow.notifications;

import bflow.auth.entities.User;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.repository.RepositoryUser;
import bflow.budget.DTO.BudgetResponse;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.aws.service.SesEmailService;
import bflow.common.i18n.MessageService;
import bflow.notifications.entity.Notification;
import bflow.notifications.enums.NotificationType;
import bflow.notifications.repository.NotificationRepository;
import bflow.notifications.service.NotificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                emailService,
                repositoryUser,
                emailTemplateService,
                messageService
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
    void sendBudgetGroupSuccess_emptyMemberList_sendsNothing() {
        BudgetResponse response = new BudgetResponse();

        notificationService.sendBudgetGroupSuccess(
                List.of(), "Household", response);

        verify(notificationRepository, never()).save(any());
        verify(emailTemplateService, never())
                .sendBudgetGroupSuccessEmail(any(), any(), any(), any(), any());
    }
}