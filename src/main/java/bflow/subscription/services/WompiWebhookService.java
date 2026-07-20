package bflow.subscription.services;

import bflow.subscription.entities.Payment;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.PaymentStatus;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.gateway.dto.WompiWebhookPayload;
import bflow.subscription.repository.RepositoryPayment;
import bflow.subscription.repository.RepositoryPlan;
import bflow.subscription.repository.RepositorySubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WompiWebhookService {

    private final RepositoryPlan repositoryPlan;
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

        log.debug("Payload de webhook parseado: idTransaccion={}, enlacePago={}, resultado={}",
                payload.idTransaccion(), payload.enlacePago().id(), payload.resultadoTransaccion());

        // Idempotencia: si ya procesamos esta transacción, no hagas nada más.
        if (repositoryPayment.existsByProviderPaymentId(
                payload.idTransaccion())
            ) {
            return;
        }

        if (!"ExitosaAprobada".equals(payload.resultadoTransaccion())) {
            return; // no interesa registrar intentos fallidos por ahora
        }

        Plan plan = repositoryPlan.findByProviderLinkId(
                payload.enlacePago().id().toString())
                .orElseThrow(() -> new IllegalStateException(
                    "Webhook para un enlace no reconocido: "
                    + payload.enlacePago().id())
                );

        Subscription subscription = repositorySubscription
                .findByPlan_IdAndUser_EmailAndStatusIn(
                        plan.getId(),
                        payload.cliente().email(),
                        List.of(
                            SubscriptionStatus.PENDING_ACTIVATION,
                            SubscriptionStatus.ACTIVE,
                            SubscriptionStatus.PAST_DUE
                        )
                )
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró suscripción para email "
                            + payload.cliente().email()
                            + " en plan "
                            + plan.getId()
                        )
                );

        Payment payment = new Payment();
        payment.setSubscription(subscription);
        payment.setProviderPaymentId(payload.idTransaccion());
        payment.setAmount(payload.monto());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        repositoryPayment.save(payment);

        log.info("Payment creado: id={}, subscriptionId={}, monto={}",
                payment.getId(), subscription.getId(), payload.monto());

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setNextBillingAt(computeNextBillingAt(plan));
        repositorySubscription.save(subscription);
    }

    private boolean isValidSignature(
        final String rawBody, final String signature
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            
            mac.init(new SecretKeySpec(apiSecret.getBytes(
                StandardCharsets.UTF_8),
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

    private WompiWebhookPayload parse(final String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WompiWebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Payload de webhook inválido", e
            );
        }
    }

    private Instant computeNextBillingAt(final Plan plan) {
        return switch (plan.getBillingPeriod()) {
            case MONTHLY -> computeNextMonthly(plan);
            case YEARLY -> Instant.now().plus(
                365, ChronoUnit.DAYS
            );
        };
    }

    private Instant computeNextMonthly(final Plan plan) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime next = now.withDayOfMonth(
            Math.min(
                plan.getBillingDay(), now.toLocalDate().lengthOfMonth()
            ));
        if (!next.isAfter(now)) {
            next = next.plusMonths(1);
        }
        return next.toInstant();
    }
}
