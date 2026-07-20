package bflow.subscription.controllers;

import bflow.subscription.services.WompiWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/wompi")
@RequiredArgsConstructor
public final class WompiWebhookController {

    private final WompiWebhookService webhookService;

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
            log.error("Error procesando webhook de Wompi: {}", e.getMessage(), e);
            throw e;
        }
    }
}