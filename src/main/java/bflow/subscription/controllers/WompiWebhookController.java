package bflow.subscription.controllers;

import bflow.subscription.services.WompiWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/wompi")
@RequiredArgsConstructor
public final class WompiWebhookController {

    /** Service that processes Wompi webhook payloads. */
    private final WompiWebhookService webhookService;

    /**
     * Receive and process a Wompi webhook event.
     *
     * @param rawBody the raw request body
     * @param signature the request signature header
     * @return an OK response when the webhook was accepted
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody final String rawBody,
            @RequestHeader("wompi_hash") final String signature
    ) {
        log.info("Webhook de Wompi recibido, bytes={}", rawBody.length());
        try {
            webhookService.process(rawBody, signature);
            log.info("Webhook de Wompi procesado correctamente");
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            log.warn("Webhook de Wompi rechazado: firma inválida");
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error procesando webhook de Wompi: {}",
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }
}
