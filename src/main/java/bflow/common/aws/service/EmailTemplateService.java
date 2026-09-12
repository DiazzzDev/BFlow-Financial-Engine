package bflow.common.aws.service;

import bflow.auth.enums.SupportedLanguage;
import bflow.budget.DTO.BudgetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service for sending email notifications with templated content.
 *
 * <p>Every send method takes the recipient's {@link SupportedLanguage}
 * explicitly, resolved by the caller from {@code user.getLanguage()}
 * — this class never reads {@code LocaleContextHolder}, since several
 * callers (the recurring-transaction job, async listeners) run
 * outside an HTTP request thread where that context is empty.</p>
 *
 * <p>Templates live under {@code templates/en/} and
 * {@code templates/es/}. A missing Spanish template falls back to
 * English rather than failing the send — see
 * {@link #resolveTemplate}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class EmailTemplateService {

    /** Template engine for rendering email templates. */
    private final TemplateEngine templateEngine;
    /** Service for sending emails via AWS SES. */
    private final SesEmailService sesEmailService;
    /** Source of localized subject lines. */
    private final MessageSource messageSource;

    /** Frontend URL for email links. */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Support email address for customer inquiries. */
    @Value("${support.email}")
    private String supportEmail;

    /** URL for the application logo in emails. */
    @Value("${app.email.logo-url}")
    private String logoUrl;

    /** Expiration time in minutes for password reset tokens. */
    @Value("${security.password-reset.expiration-minutes}")
    private Integer resetExpirationMinutes;

    /** Expiration time in hours for email verification tokens. */
    @Value("${security.email-verification.expiration-hours}")
    private Integer verificationExpirationHours;

    /**
     * Renders a template for the given language, falling back to
     * English if no translated version exists yet (e.g. a template
     * added before its Spanish translation was written) rather than
     * failing the send outright.
     *
     * @param baseName the template's base file name, without folder
     *                 or extension (e.g. {@code "forgot-password"}).
     * @param language the recipient's language preference.
     * @param context the Thymeleaf rendering context.
     * @return the rendered HTML.
     */
    private String renderTemplate(
            final String baseName,
            final SupportedLanguage language,
            final Context context
    ) {
        String localeFolder = language == SupportedLanguage.ES
                ? "es" : "en";

        try {
            return templateEngine.process(
                    localeFolder + "/" + baseName, context
            );
        } catch (TemplateInputException ex) {
            log.warn(
                    "Missing {} email template '{}', "
                            + "falling back to English",
                    localeFolder, baseName
            );
            return templateEngine.process("en/" + baseName, context);
        }
    }

    /**
     * Resolves a localized subject line.
     *
     * @param code the message key.
     * @param language the recipient's language preference.
     * @param args optional {@link java.text.MessageFormat} arguments.
     * @return the localized subject.
     */
    private String subject(
            final String code,
            final SupportedLanguage language,
            final Object... args
    ) {
        Locale locale = language == SupportedLanguage.ES
                ? new Locale("es") : Locale.ENGLISH;
        return messageSource.getMessage(code, args, locale);
    }

    /**
     * Sends a password reset email to the user.
     * @param toEmail the recipient email address.
     * @param userName the user's name for personalization.
     * @param token the password reset token.
     * @param language the recipient's language preference.
     */
    public void sendPasswordResetEmail(
            final String toEmail,
            final String userName,
            final String token,
            final SupportedLanguage language
    ) {

        String resetUrl =
                frontendUrl
                        + "/reset-password?token="
                        + token;

        Context context = new Context();

        context.setVariable("userName", userName);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("minutes", resetExpirationMinutes);
        context.setVariable("year", Year.now().getValue());
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("logoUrl", logoUrl);

        String html = renderTemplate(
                "forgot-password", language, context
        );

        sesEmailService.sendEmail(
                toEmail,
                subject("email.forgotPassword.subject", language),
                html
        );
    }

    /**
     * Sends an email verification email to the specified recipient.
     * @param toEmail the recipient email address.
     * @param userName the user's name for personalization.
     * @param language the recipient's language preference.
     */
    public void sendEmailVerificationEmail(
            final String toEmail,
            final String userName,
            final SupportedLanguage language
            //final String token
    ) {

        String verificationUrl =
                frontendUrl
                        + "/api/auth/verify-email?token=";
        //+ token;

        Context context = new Context();

        context.setVariable("userName", userName);
        context.setVariable("verificationUrl", verificationUrl);
        context.setVariable("year", Year.now().getValue());
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("logoUrl", logoUrl);

        String html = renderTemplate(
                "email-verification", language, context
        );

        sesEmailService.sendEmail(
                toEmail,
                subject("email.emailVerification.subject", language),
                html
        );
    }

    /**
     * Send a renewal reminder email for an upcoming subscription renewal.
     *
     * @param toEmail recipient email address
     * @param userName recipient display name
     * @param planName the subscription plan name
     * @param amount the renewal amount
     * @param renewalDate the renewal date to display
     * @param checkoutUrl checkout URL for the renewal flow
     * @param language the recipient's language preference.
     */
    public void sendRenewalReminderEmail(
            final String toEmail,
            final String userName,
            final String planName,
            final String amount,
            final String renewalDate,
            final String checkoutUrl,
            final SupportedLanguage language
    ) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("planName", planName);
        context.setVariable("amount", amount);
        context.setVariable("renewalDate", renewalDate);
        context.setVariable("checkoutUrl", checkoutUrl);
        context.setVariable("year", Year.now().getValue());
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("logoUrl", logoUrl);

        String html = renderTemplate(
                "renewal-reminder", language, context
        );
        sesEmailService.sendEmail(
                toEmail,
                subject(
                        "email.renewalReminder.subject", language, planName
                ),
                html
        );
    }

    /**
     * Sends a wallet collaboration invitation email.
     *
     * The email contains the inviter's name, the wallet name,
     * an invitation link, and the invitation expiration date.
     *
     * @param toEmail the recipient email address
     * @param inviterName the name of the user sending the invitation
     * @param walletName the name of the shared wallet
     * @param token the invitation token used to build the invitation URL
     * @param expiresAt the invitation expiration timestamp
     * @param language the recipient's language preference.
     */
    public void sendWalletInvitationEmail(
            final String toEmail,
            final String inviterName,
            final String walletName,
            final String token,
            final Instant expiresAt,
            final SupportedLanguage language
    ) {

        String invitationUrl =
                frontendUrl.replaceAll("/+$", "")
                        + "/invitations/"
                        + token;

        String formattedExpiresAt =
                DateTimeFormatter.ofPattern(
                                "MMMM d, yyyy 'at' h:mm a z",
                                Locale.ENGLISH
                        )
                        .format(expiresAt.atZone(ZoneOffset.UTC));

        Context context = new Context();

        context.setVariable("inviterName", inviterName);
        context.setVariable("walletName", walletName);
        context.setVariable("invitationUrl", invitationUrl);
        context.setVariable("expiresAt", formattedExpiresAt);
        context.setVariable("year", Year.now().getValue());
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("logoUrl", logoUrl);

        String html = renderTemplate(
                "wallet-invitation", language, context
        );

        sesEmailService.sendEmail(
                toEmail,
                subject(
                        "email.walletInvitation.subject",
                        language,
                        inviterName
                ),
                html
        );
    }

    /**
     * Sends a notification email when a recurring transaction fails to
     * execute (e.g. insufficient wallet balance).
     *
     * @param toEmail recipient email address
     * @param userName recipient display name
     * @param transactionTitle title of the recurring transaction
     * @param amount the transaction amount
     * @param attempts number of consecutive failed attempts so far
     * @param deactivated whether the recurring transaction was
     *        auto-deactivated after reaching the failure threshold
     * @param reason short description of why the execution failed
     * @param language the recipient's language preference.
     */
    public void sendRecurringFailedEmail(
            final String toEmail,
            final String userName,
            final String transactionTitle,
            final BigDecimal amount,
            final int attempts,
            final boolean deactivated,
            final String reason,
            final SupportedLanguage language
    ) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("transactionTitle", transactionTitle);
        context.setVariable("amount", amount);
        context.setVariable("attempts", attempts);
        context.setVariable("deactivated", deactivated);
        context.setVariable("reason", reason);
        context.setVariable("manageUrl",
                frontendUrl.replaceAll("/+$", "") + "/recurring");
        context.setVariable("year", Year.now().getValue());
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("logoUrl", logoUrl);

        String html = renderTemplate(
                "recurring-transaction-failed", language, context);

        String subject = deactivated
                ? subject(
                "email.recurringFailed.deactivated.subject",
                language, transactionTitle
        )
                : subject(
                "email.recurringFailed.retrying.subject",
                language, transactionTitle
        );

        sesEmailService.sendEmail(toEmail, subject, html);
    }

    /**
     * Sends a celebratory email to one member of a shared wallet when
     * that wallet stays within budget as a team for the period.
     *
     * @param toEmail recipient email address
     * @param userName recipient display name
     * @param walletName the shared wallet's name
     * @param budget the final budget figures for the completed period
     * @param language the recipient's language preference.
     */
    public void sendBudgetGroupSuccessEmail(
            final String toEmail,
            final String userName,
            final String walletName,
            final BudgetResponse budget,
            final SupportedLanguage language
    ) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("walletName", walletName);
        context.setVariable("budgetLimit", budget.getBudgetLimit());
        context.setVariable("spent", budget.getSpent());
        context.setVariable("percentage", budget.getPercentage());
        context.setVariable("manageUrl",
                frontendUrl.replaceAll("/+$", "") + "/budgets");
        context.setVariable("year", Year.now().getValue());
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("logoUrl", logoUrl);

        String html = renderTemplate(
                "budget-group-success", language, context);

        sesEmailService.sendEmail(
                toEmail,
                subject(
                        "email.budgetGroupSuccess.subject",
                        language, walletName
                ),
                html
        );
    }

    /**
     * Sends a contact form message to the configured support email.
     * Always in English — this is internal, addressed to your own
     * support team, not user-facing content.
     *
     * @param senderName name of the person submitting the form
     * @param senderEmail email address of the person submitting the form
     * @param subject subject provided by the sender
     * @param message message provided by the sender
     */
    public void sendContactMessage(
            final String senderName,
            final String senderEmail,
            final String subject,
            final String message
    ) {
        Context context = new Context();

        context.setVariable("senderName", senderName);
        context.setVariable("senderEmail", senderEmail);
        context.setVariable("subject", subject);
        context.setVariable("message", message);
        context.setVariable("year", Year.now().getValue());
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("logoUrl", logoUrl);

        String html = templateEngine.process(
                "en/contact-message",
                context
        );

        sesEmailService.sendEmail(
                supportEmail,
                "[BFlow Contact] " + subject,
                html
        );
    }
}
