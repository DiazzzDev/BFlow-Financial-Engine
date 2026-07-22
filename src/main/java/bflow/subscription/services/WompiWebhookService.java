package bflow.subscription.services;

import bflow.subscription.entities.Payment;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.PaymentStatus;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.gateway.dto.WompiWebhookPayload;
import bflow.subscription.repository.RepositoryPayment;
import bflow.subscription.repository.RepositorySubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WompiWebhookService {

    private final RepositorySubscription repositorySubscription;
    private final RepositoryPayment repositoryPayment;
    private final ObjectMapper objectMapper;

    @Value("${wompi.api-secret}")
    private String apiSecret;

    @Transactional
    public void process(final String rawBody, final String signature) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("HMAC inválido en webhook de Wompi");
            throw new SecurityException("Firma de webhook inválida");
        }

        WompiWebhookPayload payload = parse(rawBody);

        if (repositoryPayment.existsByProviderPaymentId(payload.idTransaccion())) {
            return;
        }
        if (!"ExitosaAprobada".equals(payload.resultadoTransaccion())) {
            return;
        }

        String idSuscripcion = payload.cliente().idSuscripcion();

        Subscription subscription = repositorySubscription
                .findByProviderSubscriberId(idSuscripcion)
                .or(() -> repositorySubscription
                        .findByUser_EmailAndBillingAmountAndStatus(
                                payload.cliente().email(),
                                payload.monto(),
                                SubscriptionStatus.PENDING_ACTIVATION)
                        .stream().findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró suscripción para email " + payload.cliente().email()
                                + " idSuscripcion=" + idSuscripcion));

        if (subscription.getProviderSubscriberId() == null) {
            subscription.setProviderSubscriberId(idSuscripcion);
        }

        activate(subscription, payload.idTransaccion(), payload.monto());
    }

    /** Reusable: activa una suscripción y registra el pago. Usado por webhook y reconciliación. */
    public void activate(final Subscription subscription, final String providerPaymentId, final BigDecimal monto) {
        Payment payment = new Payment();
        payment.setUser(subscription.getUser());
        payment.setSubscription(subscription);
        payment.setProviderPaymentId(providerPaymentId);
        payment.setAmount(monto);
        payment.setCurrency("USD");
        payment.setProvider("WOMPI");
        payment.setReference(providerPaymentId); // Wompi no manda una referencia propia distinta al idTransaccion; la reusamos
        payment.setIdempotencyKey(UUID.randomUUID());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProcessedAt(Instant.now());
        repositoryPayment.save(payment);

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextBillingAt(computeNextBillingAt(subscription));
        repositorySubscription.save(subscription);

        log.info("Subscription {} activada (providerPaymentId={})", subscription.getId(), providerPaymentId);
    }

    private boolean isValidSignature(final String rawBody, final String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private WompiWebhookPayload parse(final String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WompiWebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload de webhook inválido", e);
        }
    }

    private Instant computeNextBillingAt(final Subscription subscription) {
        return switch (subscription.getPlan().getBillingPeriod()) {
            case MONTHLY -> computeNextMonthly(subscription);
            case YEARLY -> Instant.now().plus(365, ChronoUnit.DAYS);
        };
    }

    private Instant computeNextMonthly(final Subscription subscription) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime next = now.withDayOfMonth(
                Math.min(subscription.getBillingDay(), now.toLocalDate().lengthOfMonth()));
        if (!next.isAfter(now)) {
            next = next.plusMonths(1);
        }
        return next.toInstant();
    }
}
