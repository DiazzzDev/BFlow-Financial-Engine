package Diaz.Dev.BFlow.subscription.controllers;

import bflow.subscription.controllers.WompiWebhookController;
import bflow.subscription.services.WompiWebhookService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WompiWebhookControllerTest {

    private final WompiWebhookService webhookService =
            mock(WompiWebhookService.class);
    private final WompiWebhookController controller =
            new WompiWebhookController(webhookService);

    @Test
    void acceptedWebhookReturnsEmptyOkAndPassesRawPayloadAndSignature()
            throws Exception {
        String rawBody = "{\"IdTransaccion\":\"tx-1\"}";

        var response = controller.receive(rawBody, "valid-signature");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
        verify(webhookService).process(rawBody, "valid-signature");
    }

    @Test
    void invalidSignatureIsRethrownForSecurityExceptionMapping() {
        String rawBody = "{}";
        doThrow(new SecurityException("invalid signature"))
                .when(webhookService).process(rawBody, "invalid");

        assertThatThrownBy(() -> controller.receive(rawBody, "invalid"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("invalid signature");
    }

    @Test
    void processingFailureIsRethrownForGlobalErrorHandling() {
        String rawBody = "{}";
        doThrow(new IllegalStateException("subscription not found"))
                .when(webhookService).process(rawBody, "signature");

        assertThatThrownBy(() -> controller.receive(rawBody, "signature"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("subscription not found");
    }
}
