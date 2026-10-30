package Diaz.Dev.BFlow.subscription.services;

import bflow.subscription.entities.Payment;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.PaymentStatus;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.WompiApiClient;
import bflow.subscription.repository.RepositoryPayment;
import bflow.subscription.repository.RepositorySubscription;
import bflow.auth.entities.User;
import bflow.common.i18n.MessageService;
import bflow.subscription.services.WompiWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WompiWebhookServiceTest {

    private static final String SECRET = "test-secret";

    @Mock private RepositorySubscription repositorySubscription;
    @Mock private RepositoryPayment repositoryPayment;
    @Mock private WompiApiClient wompiApiClient;
    @Mock private MessageService messageService;

    private WompiWebhookService service;

    @BeforeEach
    void setUp() {
        lenient().when(messageService.get(eq("payment.amount.mismatch")))
                .thenReturn("Monto del pago no coincide con el monto "
                        + "esperado de la suscripción");
        service = new WompiWebhookService(
                repositorySubscription, wompiApiClient, repositoryPayment,
                new ObjectMapper(), messageService
        );
        ReflectionTestUtils.setField(service, "apiSecret", SECRET);
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String recurringPayload(String idTransaccion, String idSuscripcion, String email, String monto) {
        return """
            {
              "IdTransaccion": "%s",
              "ResultadoTransaccion": "ExitosaAprobada",
              "Monto": %s,
              "EsProductiva": false,
              "EnlacePago": { "Id": null, "IdentificadorEnlaceComercio": null, "NombreProducto": null, "DescripcionProducto": null },
              "Cliente": { "EMail": "%s", "IdSuscripcion": "%s", "Nombre": "Test", "DiaPagoSuscripcion": "21" }
            }
            """.formatted(idTransaccion, monto, email, idSuscripcion);
    }

    private String oneTimePayload(
            String transactionId,
            String checkoutReference,
            String amount
    ) {
        return """
            {"IdTransaccion":"%s","ResultadoTransaccion":"ExitosaAprobada",
             "Monto":%s,"EnlacePago":{"Id":"link-1",
             "IdentificadorEnlaceComercio":"%s"},
             "Cliente":{"EMail":"annual@example.test","IdSuscripcion":null}}
            """.formatted(transactionId, amount, checkoutReference);
    }

    private Subscription monthlySubscription(BigDecimal amount) {
        Plan plan = new Plan();
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        User user = new User();
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setPlan(plan);
        subscription.setUser(user);
        subscription.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
        subscription.setBillingAmount(amount);
        subscription.setBillingDay(21);
        return subscription;
    }

    private Subscription annualSubscription(String checkoutReference) {
        Plan plan = new Plan();
        plan.setBillingPeriod(BillingPeriod.YEARLY);
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setPlan(plan);
        subscription.setUser(new User());
        subscription.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
        subscription.setBillingAmount(new BigDecimal("150.00"));
        subscription.setCheckoutReference(checkoutReference);
        return subscription;
    }

    @Test
    void rechazaFirmaInvalida() {
        String body = recurringPayload(UUID.randomUUID().toString(), "sub-1", "a@a.com", "15.00");
        assertThatThrownBy(() -> service.process(body, "firma-incorrecta"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(repositoryPayment);
    }

    @Test
    void esIdempotenteAntePagoYaRegistrado() throws Exception {
        String idTransaccion = UUID.randomUUID().toString();
        String body = recurringPayload(idTransaccion, "sub-1", "a@a.com", "15.00");
        when(repositoryPayment.existsByProviderPaymentId(idTransaccion)).thenReturn(true);

        service.process(body, sign(body));

        verify(repositorySubscription, never()).findByProviderSubscriberId(any());
        verify(repositoryPayment, never()).save(any());
    }

    @Test
    void ignoraResultadoNoAprobado() throws Exception {
        String body = """
            {"IdTransaccion":"%s","ResultadoTransaccion":"Rechazada","Monto":15.00,
             "EnlacePago":{"Id":null,"IdentificadorEnlaceComercio":null},
             "Cliente":{"EMail":"a@a.com","IdSuscripcion":"sub-1"}}
            """.formatted(UUID.randomUUID());
        when(repositoryPayment.existsByProviderPaymentId(any())).thenReturn(false);

        service.process(body, sign(body));

        verify(repositorySubscription, never()).findByProviderSubscriberId(any());
    }

    @Test
    void payloadMalformadoLanzaExcepcion() {
        String body = "{ esto no es json valido";
        assertThatThrownBy(() -> service.process(body, "cualquier-cosa"))
                .isInstanceOf(SecurityException.class); // falla primero en la firma, es lo esperado
    }

    @Test
    void matcheaSuscripcionMensualPorProviderSubscriberId() throws Exception {
        String idTransaccion = UUID.randomUUID().toString();
        String body = recurringPayload(idTransaccion, "sub-real-1", "a@a.com", "15.00");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        subscription.setProviderSubscriberId("sub-real-1");

        when(repositoryPayment.existsByProviderPaymentId(idTransaccion)).thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-real-1"))
                .thenReturn(Optional.of(subscription));

        service.process(body, sign(body));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(repositoryPayment).save(any(Payment.class));
    }

    /*
    @Test
    void primerCobroCaeAFallbackPorEmailYMontoCuandoNoHaySubscriberIdGuardado() throws Exception {
        String idTransaccion = UUID.randomUUID().toString();
        String body = recurringPayload(idTransaccion, "sub-nuevo", "a@a.com", "15.00");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        // providerSubscriberId aún null: primer cobro de esta suscripción

        when(repositoryPayment.existsByProviderPaymentId(idTransaccion)).thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-nuevo")).thenReturn(Optional.empty());
        when(repositorySubscription.findByUser_EmailAndBillingAmountAndStatus(
                "a@a.com", new BigDecimal("15.00"), SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(List.of(subscription));

        service.process(body, sign(body));

        assertThat(subscription.getProviderSubscriberId()).isEqualTo("sub-nuevo");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

     */

    @Test
    void rechazaCuandoMontoNoCoincideConLaSuscripcion() throws Exception {
        String idTransaccion = UUID.randomUUID().toString();
        String body = recurringPayload(idTransaccion, "sub-1", "a@a.com", "9999.00");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        subscription.setProviderSubscriberId("sub-1");

        when(repositoryPayment.existsByProviderPaymentId(idTransaccion)).thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-1")).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.process(body, sign(body)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Monto");

        verify(repositoryPayment, never()).save(any());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);
    }

    @Test
    void matcheaSuscripcionAnualPorCheckoutReference() throws Exception {
        String idTransaccion = UUID.randomUUID().toString();
        String reference = UUID.randomUUID().toString();
        String body = """
            {"IdTransaccion":"%s","ResultadoTransaccion":"ExitosaAprobada","Monto":150.00,
             "EnlacePago":{"Id":123,"IdentificadorEnlaceComercio":"%s"},
             "Cliente":{"EMail":"a@a.com","IdSuscripcion":null}}
            """.formatted(idTransaccion, reference);

        Plan plan = new Plan();
        plan.setBillingPeriod(BillingPeriod.YEARLY);
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(new User());
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
        subscription.setBillingAmount(new BigDecimal("150.00"));
        subscription.setCheckoutReference(reference);

        when(repositoryPayment.existsByProviderPaymentId(idTransaccion)).thenReturn(false);
        when(repositorySubscription.findByCheckoutReferenceAndStatus(reference, SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(Optional.of(subscription));

        service.process(body, sign(body));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextBillingAt()).isAfter(Instant.now());
        assertThat(subscription.getEndsAt())
                .isEqualTo(subscription.getNextBillingAt());
        verify(repositoryPayment).save(any(Payment.class));
    }

    @Test
    void annualRenewalStartsAfterExistingPaidTermAndAddsAFullYear()
            throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String reference = UUID.randomUUID().toString();
        String body = oneTimePayload(transactionId, reference, "150.00");
        Subscription renewal = annualSubscription(reference);
        Subscription current = annualSubscription("old-reference");
        current.setStatus(SubscriptionStatus.ACTIVE);
        Instant currentPaidThrough = Instant.now().plusSeconds(10 * 24 * 60 * 60);
        current.setEndsAt(currentPaidThrough);
        current.setNextBillingAt(currentPaidThrough);
        current.setUser(renewal.getUser());
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByCheckoutReferenceAndStatus(reference,
                SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(Optional.of(renewal));
        when(repositorySubscription.findByUserIdAndStatus(
                renewal.getUser().getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        service.process(body, sign(body));

        assertThat(renewal.getStartsAt()).isEqualTo(currentPaidThrough);
        assertThat(renewal.getEndsAt()).isEqualTo(
                currentPaidThrough.plus(365, java.time.temporal.ChronoUnit.DAYS));
        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(repositoryPayment).save(any(Payment.class));
        verify(wompiApiClient, never()).deactivateRecurringLink(any());
    }

    @Test
    void primerPagoRecurrenteGuardaElIdDelSuscriptorEnElFallbackPorEmail()
            throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = recurringPayload(
                transactionId, "new-subscriber", "a@a.com", "15.00");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("new-subscriber"))
                .thenReturn(Optional.empty());
        when(repositorySubscription.findByUserEmailAndBillingAmountAndStatus(
                "a@a.com", new BigDecimal("15.00"),
                SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(List.of(subscription));

        service.process(body, sign(body));

        assertThat(subscription.getProviderSubscriberId())
                .isEqualTo("new-subscriber");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(repositorySubscription).findByUserEmailAndBillingAmountAndStatus(
                "a@a.com", new BigDecimal("15.00"),
                SubscriptionStatus.PENDING_ACTIVATION);
    }

    @Test
    void recurrentPaymentWithoutMatchingSubscriptionIsRejected() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = recurringPayload(
                transactionId, "missing-subscriber", "missing@example.test", "15");
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("missing-subscriber"))
                .thenReturn(Optional.empty());
        when(repositorySubscription.findByUserEmailAndBillingAmountAndStatus(
                "missing@example.test", new BigDecimal("15"),
                SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.process(body, sign(body)))
                .isInstanceOf(IllegalStateException.class);
        verify(repositoryPayment, never()).save(any());
    }

    @Test
    void checkoutPaymentWithoutMatchingReferenceIsRejected() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = oneTimePayload(transactionId, "unknown-reference", "150");
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByCheckoutReferenceAndStatus(
                "unknown-reference", SubscriptionStatus.PENDING_ACTIVATION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(body, sign(body)))
                .isInstanceOf(IllegalStateException.class);
        verify(repositoryPayment, never()).save(any());
    }

    @Test
    void signedMalformedPayloadIsReportedAsInvalidPayload() throws Exception {
        String body = "{malformed json";

        assertThatThrownBy(() -> service.process(body, sign(body)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repositoryPayment, repositorySubscription);
    }

    @Test
    void bodyModifiedAfterSigningIsRejectedBeforeRepositoryAccess()
            throws Exception {
        String signedBody = recurringPayload(
                UUID.randomUUID().toString(), "sub-1", "a@a.com", "15.00");
        String tamperedBody = signedBody.replace("15.00", "16.00");

        assertThatThrownBy(() -> service.process(tamperedBody, sign(signedBody)))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(repositoryPayment, repositorySubscription);
    }

    @Test
    void successfulRecurringPaymentPersistsProviderAndAmountDetails()
            throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = recurringPayload(transactionId, "sub-1", "a@a.com", "15");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        subscription.setProviderSubscriberId("sub-1");
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-1"))
                .thenReturn(Optional.of(subscription));

        service.process(body, sign(body));

        org.mockito.ArgumentCaptor<Payment> paymentCaptor =
                org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(repositoryPayment).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertThat(payment.getUser()).isSameAs(subscription.getUser());
        assertThat(payment.getSubscription()).isSameAs(subscription);
        assertThat(payment.getProviderPaymentId()).isEqualTo(transactionId);
        assertThat(payment.getReference()).isEqualTo(transactionId);
        assertThat(payment.getProvider()).isEqualTo("WOMPI");
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getAmount()).isEqualByComparingTo("15.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getIdempotencyKey()).isNotNull();
        assertThat(payment.getProcessedAt()).isNotNull();
    }

    @Test
    void activationCancelsPreviousActiveSubscriptionForSameUser()
            throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = recurringPayload(transactionId, "sub-new", "a@a.com", "15");
        Subscription current = monthlySubscription(new BigDecimal("15.00"));
        current.setProviderSubscriberId("sub-new");
        Subscription previous = monthlySubscription(new BigDecimal("9.00"));
        previous.setStatus(SubscriptionStatus.ACTIVE);
        previous.setUser(current.getUser());
        previous.setProviderLinkId("old-link");
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-new"))
                .thenReturn(Optional.of(current));
        when(repositorySubscription.findByUserIdAndStatus(
                current.getUser().getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(previous));

        service.process(body, sign(body));

        assertThat(previous.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(previous.getCanceledAt()).isNotNull();
        assertThat(previous.isAutoRenew()).isFalse();
        assertThat(previous.getNextBillingAt()).isNull();
        verify(wompiApiClient).deactivateRecurringLink("old-link");
        verify(repositorySubscription).save(previous);
        verify(repositorySubscription).save(current);
    }

    @Test
    void activationDoesNotCancelTheSameSubscriptionReturnedAsActive()
            throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = recurringPayload(transactionId, "sub-same", "a@a.com", "15");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        subscription.setProviderSubscriberId("sub-same");
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-same"))
                .thenReturn(Optional.of(subscription));
        when(repositorySubscription.findByUserIdAndStatus(
                subscription.getUser().getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));

        service.process(body, sign(body));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCanceledAt()).isNull();
        verify(repositorySubscription, times(1)).save(subscription);
    }

    @Test
    @Test
    void monthlyActivationSchedulesFutureBillingAndKeepsNoEndDate()
            throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = recurringPayload(
                transactionId, "sub-monthly", "a@a.com", "15");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        subscription.setProviderSubscriberId("sub-monthly");
        subscription.setReminderSentAt(Instant.now());
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-monthly"))
                .thenReturn(Optional.of(subscription));

        service.process(body, sign(body));

        Instant nextBillingAt = subscription.getNextBillingAt();
        assertThat(nextBillingAt).isAfter(Instant.now());
        assertThat(nextBillingAt.atZone(ZoneOffset.UTC).getDayOfMonth())
                .isEqualTo(21);
        assertThat(subscription.getEndsAt()).isNull();
        assertThat(subscription.getReminderSentAt()).isNull();
    }

    @Test
    void amountComparisonAcceptsDifferentBigDecimalScale() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        String body = recurringPayload(
                transactionId, "sub-scale", "a@a.com", "15.0");
        Subscription subscription = monthlySubscription(new BigDecimal("15.00"));
        subscription.setProviderSubscriberId("sub-scale");
        when(repositoryPayment.existsByProviderPaymentId(transactionId))
                .thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-scale"))
                .thenReturn(Optional.of(subscription));

        service.process(body, sign(body));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(repositoryPayment).save(any(Payment.class));
    }

    /*
    @Test
    void suscripcionNoEncontradaLanzaExcepcion() throws Exception {
        String idTransaccion = UUID.randomUUID().toString();
        String body = recurringPayload(idTransaccion, "sub-fantasma", "nadie@nadie.com", "15.00");

        when(repositoryPayment.existsByProviderPaymentId(idTransaccion)).thenReturn(false);
        when(repositorySubscription.findByProviderSubscriberId("sub-fantasma")).thenReturn(Optional.empty());
        when(repositorySubscription.findByUser_EmailAndBillingAmountAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.process(body, sign(body)))
                .isInstanceOf(IllegalStateException.class);
    }

     */
}
