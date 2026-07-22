package bflow.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class WompiApiClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String idAplicativo;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public WompiApiClient(
            RestClient.Builder builder,
            @Value("${wompi.client-id}") String clientId,
            @Value("${wompi.client-secret}") String clientSecret,
            @Value("${wompi.app-id}") String idAplicativo
    ) {
        this.restClient = builder.baseUrl("https://api.wompi.sv").build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.idAplicativo = idAplicativo;
    }

    private synchronized String getAccessToken() {
        if (Instant.now().isBefore(tokenExpiry)) return cachedToken;

        var response = RestClient.create("https://id.wompi.sv")
                .post()
                .uri("/connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=%s&client_secret=%s"
                        .formatted(clientId, clientSecret))
                .retrieve()
                .body(TokenResponse.class);

        cachedToken = response.accessToken();
        tokenExpiry = Instant.now().plusSeconds(response.expiresIn() - 30); // margen
        return cachedToken;
    }

    public RecurringLinkResponse createRecurringLink(
        int diaDePago,
        String nombre,
        BigDecimal monto,
        String descripcion
    ) {
        var body = Map.of(
            "diaDePago", diaDePago,
            "nombre", nombre,
            "idAplicativo", idAplicativo,
            "monto", monto,
            "descripcionProducto", descripcion
        );
        return restClient.post()
            .uri("/EnlacePagoRecurrente")
            .header("Authorization", "Bearer " + getAccessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(RecurringLinkResponse.class);
    }

    public List<SubscriberInfo> getSubscribers(String idEnlace) {
        return restClient.get()
            .uri("/EnlacePagoRecurrente/{id}/suscripciones", idEnlace)
            .header("Authorization", "Bearer " + getAccessToken())
            .retrieve()
            .body(new ParameterizedTypeReference<List<SubscriberInfo>>() {});
    }

    public void deactivateRecurringLink(String idEnlace) {
        restClient.post()
                .uri("/EnlacePagoRecurrente/{id}", idEnlace)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .toBodilessEntity();
    }

    public PaymentLinkResponse createPaymentLink(
        String identificadorEnlaceComercio, BigDecimal monto, String nombreProducto) {
        var formaPago = Map.of(
                "permitirTarjetaCreditoDebido", true,
                "permitirPagoConPuntoAgricola", false,
                "permitirPagoEnCuotasAgricola", false,
                "permitirPagoEnBitcoin", false,
                "permitePagoQuickPay", false
        );
        var body = Map.of(
                "identificadorEnlaceComercio", identificadorEnlaceComercio,
                "monto", monto,
                "nombreProducto", nombreProducto,
                "formaPago", formaPago
        );
        return restClient.post()
        .uri("/EnlacePago")
        .header("Authorization", "Bearer " + getAccessToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(PaymentLinkResponse.class);
    }

    public record PaymentLinkResponse(
            long idEnlace,           // ⚠️ doc dice Entero, no string — verificar contra POST real, ya nos pasó antes con recurrente
            String urlQrCodeEnlace,
            String urlEnlace,
            boolean estaProductivo
    ) { }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") int expiresIn
    ) { }

    public record RecurringLinkResponse(
            String id,
            String urlCortaSuscribirse,
            String urlLargaSuscribirse,
            String urlSuscribirseQr,
            boolean estaActivo
    ) { }

    public record SubscriberInfo(
            String id,
            Instant fechaCreacion,
            String alias,
            BigDecimal monto,
            int pagosRealizados,
            String estado,
            String idSuscriptor,      // es el email del suscriptor
            String nombreSuscriptor,
            Instant fechaInicio,
            Integer diaPago
    ) { }
}
