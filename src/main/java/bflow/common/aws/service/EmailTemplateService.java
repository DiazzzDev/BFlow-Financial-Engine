package bflow.common.aws.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final TemplateEngine templateEngine;
    private final SesEmailService sesEmailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${support.email}")
    private String supportEmail;

    @Value("${app.email.logo-url}")
    private String logoUrl;

    @Value("${security.password-reset.expiration-minutes}")
    private Integer resetExpirationMinutes;

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
