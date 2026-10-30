package Diaz.Dev.BFlow.common.aws.service;

import bflow.auth.enums.SupportedLanguage;
import bflow.budget.DTO.BudgetResponse;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.aws.service.SesEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailTemplateServiceCoverageTest {

    private TemplateEngine templateEngine;
    private SesEmailService emailService;
    private MessageSource messageSource;
    private EmailTemplateService service;

    @BeforeEach
    void setUp() {
        templateEngine = mock(TemplateEngine.class);
        emailService = mock(SesEmailService.class);
        messageSource = mock(MessageSource.class);
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageSource.getMessage(
                anyString(),
                org.mockito.ArgumentMatchers.<Object[]>any(),
                any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new EmailTemplateService(
                templateEngine, emailService, messageSource);
        ReflectionTestUtils.setField(
                service, "frontendUrl", "https://app.example/");
        ReflectionTestUtils.setField(
                service, "supportEmail", "support@example.com");
        ReflectionTestUtils.setField(
                service, "logoUrl", "https://app.example/logo.png");
        ReflectionTestUtils.setField(service, "resetExpirationMinutes", 30);
        ReflectionTestUtils.setField(
                service, "verificationExpirationHours", 24);
    }

    @Test
    void sendsAllSupportedEmailTypesUsingLocalizedSubjectsAndTemplates() {
        service.sendPasswordResetEmail(
                "user@example.com", "User", "reset-token",
                SupportedLanguage.EN);
        service.sendEmailVerificationEmail(
                "user@example.com", "User", SupportedLanguage.ES);
        service.sendAccountDeletionRequestedEmail(
                "user@example.com", "User", Instant.parse(
                        "2030-01-02T03:04:05Z"), SupportedLanguage.ES);
        service.sendAccountDeletionCancelledEmail(
                "user@example.com", "User", SupportedLanguage.EN);
        service.sendRenewalReminderEmail(
                "user@example.com", "User", "Premium", "9.99",
                "2030-02-01", "https://checkout.example",
                SupportedLanguage.ES);
        service.sendWalletInvitationEmail(
                "invitee@example.com", "Owner", "Shared wallet",
                "invite-token", Instant.parse("2030-01-02T03:04:05Z"),
                SupportedLanguage.EN);
        service.sendRecurringFailedEmail(
                "user@example.com", "User", "Rent",
                new BigDecimal("125.00"), 2, false, "Insufficient funds",
                SupportedLanguage.EN);
        service.sendRecurringFailedEmail(
                "user@example.com", "User", "Rent",
                new BigDecimal("125.00"), 5, true, "Insufficient funds",
                SupportedLanguage.ES);

        BudgetResponse budget = mock(BudgetResponse.class);
        when(budget.getBudgetLimit()).thenReturn(new BigDecimal("500.00"));
        when(budget.getSpent()).thenReturn(new BigDecimal("420.00"));
        when(budget.getPercentage()).thenReturn(84);
        service.sendBudgetGroupSuccessEmail(
                "user@example.com", "User", "Shared wallet", budget,
                SupportedLanguage.ES);
        service.sendContactMessage(
                "Sender", "sender@example.com", "Question", "Hello");

        verify(emailService).sendEmail(
                "user@example.com", "email.forgotPassword.subject",
                "en/forgot-password");
        verify(emailService).sendEmail(
                "user@example.com", "email.emailVerification.subject",
                "es/email-verification");
        verify(emailService).sendEmail(
                "user@example.com", "email.accountDeletionRequested.subject",
                "es/account-deletion-requested");
        verify(emailService).sendEmail(
                "user@example.com", "email.accountDeletionCancelled.subject",
                "en/account-deletion-cancelled");
        verify(emailService).sendEmail(
                "user@example.com", "email.renewalReminder.subject",
                "es/renewal-reminder");
        verify(emailService).sendEmail(
                "invitee@example.com", "email.walletInvitation.subject",
                "en/wallet-invitation");
        verify(emailService).sendEmail(
                "user@example.com", "email.recurringFailed.retrying.subject",
                "en/recurring-transaction-failed");
        verify(emailService).sendEmail(
                "user@example.com",
                "email.recurringFailed.deactivated.subject",
                "es/recurring-transaction-failed");
        verify(emailService).sendEmail(
                "user@example.com", "email.budgetGroupSuccess.subject",
                "es/budget-group-success");
        verify(emailService).sendEmail(
                "support@example.com", "[BFlow Contact] Question",
                "en/contact-message");
        verify(templateEngine).process(
                eq("en/forgot-password"), any(Context.class));
    }
}
