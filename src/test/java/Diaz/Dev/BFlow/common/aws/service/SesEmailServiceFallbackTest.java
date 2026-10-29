package Diaz.Dev.BFlow.common.aws.service;

import bflow.common.aws.service.SesEmailService;
import bflow.common.exception.EmailDeliveryException;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesEmailServiceFallbackTest {

    @Mock private SesClient sesClient;
    @Mock private MessageService messageService;

    private SesEmailService service;

    @BeforeEach
    void setUp() {
        service = new SesEmailService(sesClient, messageService);
        ReflectionTestUtils.setField(service, "from", "no-reply@example.test");
    }

    @Test
    void sendsUtf8HtmlMessageToRequestedRecipient() {
        service.sendEmail("user@example.test", "Subject", "<b>Body</b>");

        org.mockito.ArgumentCaptor<SendEmailRequest> captor =
                org.mockito.ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();
        assertThat(request.destination().toAddresses()).containsExactly("user@example.test");
        assertThat(request.message().subject().charset()).isEqualTo("UTF-8");
        assertThat(request.message().body().html().data()).isEqualTo("<b>Body</b>");
    }

    @Test
    void wrapsSesOutageInDomainEmailDeliveryException() {
        AwsServiceException outage = SesException.builder()
                .message("SES unavailable")
                .build();
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(outage);
        when(messageService.get("email.delivery.unavailable"))
                .thenReturn("Email unavailable");

        assertThatThrownBy(() -> service.sendEmail(
                "user@example.test", "Subject", "Body"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasCause(outage);
    }
}
