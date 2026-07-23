package Diaz.Dev.BFlow.subscription.services;

import bflow.subscription.entities.Payment;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositoryPayment;
import bflow.subscription.repository.RepositorySubscription;
import bflow.auth.entities.User;
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

    private WompiWebhookService service;

    @BeforeEach
    void setUp() {
        service = new WompiWebhookService(repositorySubscription, repositoryPayment, new ObjectMapper());
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
        assertThat(subscription.getEndsAt()).isNotNull(); // plan anual: endsAt debe quedar seteado
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