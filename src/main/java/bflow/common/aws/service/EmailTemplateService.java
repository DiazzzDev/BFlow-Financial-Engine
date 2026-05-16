package bflow.common.aws.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;

/**
 * Service for sending email notifications with templated content.
 */
@Service
@RequiredArgsConstructor
public final class EmailTemplateService {

    /** Template engine for rendering email templates. */
    private final TemplateEngine templateEngine;
    /** Service for sending emails via AWS SES. */
    private final SesEmailService sesEmailService;

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

    /**
     * Sends a password reset email to the user.
     * @param toEmail the recipient email address.
     * @param userName the user's name for personalization.
     * @param token the password reset token.
     */
    public void sendPasswordResetEmail(
            final String toEmail,
            final String userName,
            final String token
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

        String html = templateEngine.process(
                "forgot-password",
                context
        );

        sesEmailService.sendEmail(
                toEmail,
                "Reset your BFlow password",
                html
        );
    }
}
