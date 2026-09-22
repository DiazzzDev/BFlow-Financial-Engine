package Diaz.Dev.BFlow.auth.services;

import bflow.auth.entities.User;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.aws.service.SesEmailService;
import bflow.common.exception.EmailDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Verifies account-deletion email notifications with real templates
 * and the test profile, while replacing SES with a mock so no email
 * is delivered outside the test process.
 */
@SpringBootTest(classes = bflow.BFlowApplication.class)
@ActiveProfiles("test")
@Transactional
class UserServiceDeletionEmailTest {

    @Autowired private RepositoryUser repositoryUser;
    @Autowired private UserService userService;

    @MockitoBean private SesEmailService sesEmailService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("deletion-email-" + UUID.randomUUID()
                + "@example.com");
        user.setName("Deletion Email User");
        user.setLanguage(SupportedLanguage.EN);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user = repositoryUser.save(user);
        clearInvocations(sesEmailService);
    }

    @Test
    void softDelete_sendsRequestedEmailWithScheduledDeletionDate() {
        userService.softDelete(user.getId());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(sesEmailService).sendEmail(
                eq(user.getEmail()),
                eq(
                        "Your BFlow account deletion is scheduled"
                ),
                html.capture()
        );

        User saved = repositoryUser.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.PENDING_DELETION, saved.getStatus());
        assertNotNull(saved.getDeletionRequestedAt());
        assertTrue(html.getValue().contains("Account deletion scheduled"));
        assertTrue(html.getValue().contains("Cancel account deletion"));
        String expectedDeletionDate = DateTimeFormatter
                .ofLocalizedDate(FormatStyle.LONG)
                .withLocale(Locale.ENGLISH)
                .format(saved.getDeletionRequestedAt()
                        .plus(Duration.ofDays(30))
                        .atZone(ZoneOffset.UTC));
        assertTrue(html.getValue().contains(expectedDeletionDate));
    }

    @Test
    void cancelDeletion_sendsCancellationEmailAndRestoresActiveStatus() {
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now().minus(Duration.ofDays(1)));
        repositoryUser.save(user);

        userService.cancelDeletion(user.getId());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(sesEmailService).sendEmail(
                eq(user.getEmail()),
                eq(
                        "Your BFlow account deletion was cancelled"
                ),
                html.capture()
        );

        User saved = repositoryUser.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertNull(saved.getDeletionRequestedAt());
        assertTrue(html.getValue().contains("Your account is active again"));
    }

    @Test
    void softDelete_keepsPendingDeletionWhenSesIsUnavailable() {
        doThrow(new EmailDeliveryException("SES unavailable"))
                .when(sesEmailService)
                .sendEmail(anyString(), anyString(), anyString());

        userService.softDelete(user.getId());

        User saved = repositoryUser.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.PENDING_DELETION, saved.getStatus());
        assertNotNull(saved.getDeletionRequestedAt());
    }
}
