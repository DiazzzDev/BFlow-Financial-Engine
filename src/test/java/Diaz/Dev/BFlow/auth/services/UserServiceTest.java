package Diaz.Dev.BFlow.auth.services;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.User;
import bflow.auth.enums.NameSource;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.exception.EmailDeliveryException;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private RepositoryUser repositoryUser;
    private MessageService messageService;
    private EmailTemplateService emailTemplateService;
    private UserService service;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        repositoryUser = mock(RepositoryUser.class);
        messageService = mock(MessageService.class);
        emailTemplateService = mock(EmailTemplateService.class);
        service = new UserService(
                repositoryUser, messageService, emailTemplateService);
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("user@example.com")
                .name("Original Name")
                .status(UserStatus.ACTIVE)
                .build();
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void findsUserAndReportsMissingUser() {
        assertEquals(user, service.findById(userId));

        when(repositoryUser.findById(userId)).thenReturn(Optional.empty());
        when(messageService.get("user.notFound")).thenReturn("User not found");

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> service.findById(userId));
        assertEquals("User not found", error.getMessage());
    }

    @Test
    void updatesTrimmedProfileFieldsAndReturnsMappedProfile() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setEmail("  updated@example.com  ");
        request.setName("  Updated Name  ");
        request.setLanguage(SupportedLanguage.EN);

        UserProfileResponse response = service.updateProfile(userId, request);

        assertEquals("updated@example.com", user.getEmail());
        assertEquals("Updated Name", user.getName());
        assertEquals(NameSource.USER, user.getNameSource());
        assertEquals(SupportedLanguage.EN, user.getLanguage());
        assertEquals(userId, response.getId());
        assertEquals("updated@example.com", response.getEmail());
        assertEquals("Updated Name", response.getName());
        verify(repositoryUser).save(user);
    }

    @Test
    void softDeleteMarksAccountPendingAndSchedulesDeletionEmail() {
        service.softDelete(userId);

        assertEquals(UserStatus.PENDING_DELETION, user.getStatus());
        assertNotNull(user.getDeletionRequestedAt());
        verify(repositoryUser).save(user);
        verify(emailTemplateService).sendAccountDeletionRequestedEmail(
                "user@example.com", "Original Name",
                user.getDeletionRequestedAt().plus(Duration.ofDays(30)),
                user.getLanguage());
    }

    @Test
    void softDeleteKeepsPendingStateWhenEmailDeliveryFails() {
        doThrow(new EmailDeliveryException("SES unavailable"))
                .when(emailTemplateService)
                .sendAccountDeletionRequestedEmail(
                        anyString(), anyString(), any(Instant.class),
                        any(SupportedLanguage.class));

        service.softDelete(userId);

        assertEquals(UserStatus.PENDING_DELETION, user.getStatus());
        assertNotNull(user.getDeletionRequestedAt());
    }

    @Test
    void cancelDeletionRestoresAccountAndSendsConfirmation() {
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now().minus(Duration.ofDays(2)));

        service.cancelDeletion(userId);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(null, user.getDeletionRequestedAt());
        verify(repositoryUser).save(user);
        verify(emailTemplateService).sendAccountDeletionCancelledEmail(
                "user@example.com", "Original Name", user.getLanguage());
    }

    @Test
    void cancelDeletionRejectsAnAccountThatIsNotPending() {
        when(messageService.get("user.deletion.notPending"))
                .thenReturn("Deletion is not pending");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.cancelDeletion(userId));

        assertEquals("Deletion is not pending", error.getMessage());
    }

    @Test
    void reportsRemainingDeletionDaysAndFloorsExpiredGracePeriodAtZero() {
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now());
        long remaining = service.daysRemainingBeforeHardDelete(user);
        assertTrue(remaining >= 29 && remaining <= 30);

        user.setDeletionRequestedAt(Instant.now().minus(Duration.ofDays(31)));
        assertEquals(0, service.daysRemainingBeforeHardDelete(user));

        user.setStatus(UserStatus.ACTIVE);
        assertEquals(0, service.daysRemainingBeforeHardDelete(user));
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(null);
        assertEquals(0, service.daysRemainingBeforeHardDelete(user));
    }

    @Test
    void rejectsInactiveUsersAndReturnsActiveProfile() {
        assertEquals(userId, service.getProfile(userId).getId());
        user.setStatus(UserStatus.PENDING_DELETION);
        when(messageService.get("user.accountNotActive"))
                .thenReturn("Account is not active");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.validateUserActive(userId));

        assertEquals("Account is not active", error.getMessage());
    }
}
