package bflow.subscription.services;

import bflow.subscription.entities.Payment;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WompiWebhookService {

    /** Number of days used for annual subscription billing. */
    private static final int DAYS_PER_YEAR = 365;

    /** Repository used to access subscription data. */
    private final RepositorySubscription repositorySubscription;

    /** Repository used to access payment data. */
    private final RepositoryPayment repositoryPayment;

    /** Jackson mapper used to deserialize webhook payloads. */
    private final ObjectMapper objectMapper;

    /** Secret shared with Wompi to validate webhook signatures. */
    @Value("${wompi.api-secret}")
    private String apiSecret;

    /**
     * Process a Wompi webhook payload after verifying its signature.
     *
     * @param rawBody the raw webhook body
     * @param signature the signature header
     */
    @Transactional
    public void process(final String rawBody, final String signature) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("HMAC inválido en webhook de Wompi");
            throw new SecurityException("Firma de webhook inválida");
        }

        WompiWebhookPayload payload = parse(rawBody);

        if (repositoryPayment.existsByProviderPaymentId(
                payload.idTransaccion())) {
            return;
        }
        if (!"ExitosaAprobada".equals(payload.resultadoTransaccion())) {
            return;
        }

        Subscription subscription = payload.cliente().idSuscripcion() != null
                ? matchRecurring(payload)
                : matchOneTime(payload);

        activate(subscription, payload.idTransaccion(), payload.monto());
    }

    /**
     * Match a webhook to a recurring subscription.
     *
     * @param payload the webhook payload
     * @return the matching subscription
     */
    private Subscription matchRecurring(final WompiWebhookPayload payload) {
        String idSuscripcion = payload.cliente().idSuscripcion();
        Subscription subscription = repositorySubscription
                .findByProviderSubscriberId(idSuscripcion)
                .or(() -> repositorySubscription
                        .findByUserEmailAndBillingAmountAndStatus(
                                payload.cliente().email(),
                                payload.monto(),
                                SubscriptionStatus.PENDING_ACTIVATION
                        )
                        .stream()
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró suscripción para email "
                                + payload.cliente().email()
                                + " idSuscripcion=" + idSuscripcion));

        if (subscription.getProviderSubscriberId() == null) {
            subscription.setProviderSubscriberId(idSuscripcion);
        }
        return subscription;
    }

    /**
     * Match a webhook to a one-time checkout subscription.
     *
     * @param payload the webhook payload
     * @return the matching subscription
     */
    private Subscription matchOneTime(final WompiWebhookPayload payload) {
        String reference = payload.enlacePago().identificadorEnlaceComercio();
        return repositorySubscription
                .findByCheckoutReferenceAndStatus(
                        reference,
                        SubscriptionStatus.PENDING_ACTIVATION
                )
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró suscripción para checkoutReference "
                                + reference));
    }

    /**
     * Activate a subscription and register the corresponding payment.
     *
     * @param subscription the subscription to activate
     * @param providerPaymentId the provider payment identifier
     * @param monto the payment amount
     */
    public void activate(
            final Subscription subscription,
            final String providerPaymentId,
            final BigDecimal monto
    ) {
        if (monto.compareTo(subscription.getBillingAmount()) != 0) {
            log.error(
                    "Monto no coincide para subscription {}: esperado={}, "
                            + "recibido={}, providerPaymentId={}",
                    subscription.getId(),
                    subscription.getBillingAmount(),
                    monto,
                    providerPaymentId
            );
            throw new IllegalStateException(
                    "Monto del pago no coincide con el monto esperado de "
                            + "la suscripción"
            );
        }

        Payment payment = new Payment();
        payment.setUser(subscription.getUser());
        payment.setSubscription(subscription);
        payment.setProviderPaymentId(providerPaymentId);
        payment.setAmount(monto);
        payment.setCurrency("USD");
        payment.setProvider("WOMPI");
        payment.setReference(providerPaymentId);
        payment.setIdempotencyKey(UUID.randomUUID());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProcessedAt(Instant.now());
        repositoryPayment.save(payment);

        Instant nextBilling = computeNextBillingAt(subscription);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextBillingAt(nextBilling);
        if (subscription.getPlan().getBillingPeriod() == BillingPeriod.YEARLY) {
            subscription.setEndsAt(nextBilling);
        }
        repositorySubscription.save(subscription);

        log.info(
                "Subscription {} activada (providerPaymentId={})",
                subscription.getId(),
                providerPaymentId
        );
    }

    /**
     * Validate the HMAC signature sent by Wompi.
     *
     * @param rawBody the raw webhook body
     * @param signature the signature header value
     * @return true when the signature is valid
     */
    private boolean isValidSignature(
            final String rawBody,
            final String signature
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    apiSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] computed = mac.doFinal(
                    rawBody.getBytes(StandardCharsets.UTF_8)
            );
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse a webhook payload into the domain DTO.
     *
     * @param rawBody the raw webhook body
     * @return the parsed payload
     */
    private WompiWebhookPayload parse(final String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WompiWebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Payload de webhook inválido",
                    e
            );
        }
    }

    /**
     * Compute the next billing instant based on the plan cadence.
     *
     * @param subscription the activated subscription
     * @return the next billing timestamp
     */
    private Instant computeNextBillingAt(final Subscription subscription) {
        return switch (subscription.getPlan().getBillingPeriod()) {
            case MONTHLY -> computeNextMonthly(subscription);
            case YEARLY -> Instant.now().plus(DAYS_PER_YEAR, ChronoUnit.DAYS);
        };
    }

    /**
     * Compute the next billing instant for a monthly subscription.
     *
     * @param subscription the monthly subscription
     * @return the next billing timestamp
     */
    private Instant computeNextMonthly(final Subscription subscription) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime next = now.withDayOfMonth(
                Math.min(
                        subscription.getBillingDay(),
                        now.toLocalDate().lengthOfMonth()
                )
        );
        if (!next.isAfter(now)) {
            next = next.plusMonths(1);
        }
        return next.toInstant();
    }
}
