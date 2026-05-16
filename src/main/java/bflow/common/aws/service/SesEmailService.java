package bflow.common.aws.service;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Service for sending emails using AWS SES.
 */
@Service
@RequiredArgsConstructor
public final class SesEmailService {
    /**
     * AWS SES client instance.
     */
    private final SesClient sesClient;

    /**
     * Sender email address from configuration.
     */
    @Value("${aws.ses.from}")
    private String from;

    /**
     * Send an email via AWS SES.
     *
     * @param to the recipient email address
     * @param subject the email subject
     * @param html the email body
     */
    public void sendEmail(
            final String to,
            final String subject,
            final String html
    ) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(from)
                .destination(Destination.builder()
                        .toAddresses(to)
                        .build())
                .message(Message.builder()
                        .subject(Content.builder()
                                .data(subject)
                                .charset("UTF-8")
                                .build())
                        .body(Body.builder()
                                .html(
                                        Content.builder()
                                                .data(html)
                                                .charset("UTF-8")
                                                .build()
                                )
                                .build())
                        .build())
                .build();

        sesClient.sendEmail(request);
    }
}
