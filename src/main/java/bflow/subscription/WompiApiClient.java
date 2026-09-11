package bflow.subscription;

import bflow.common.i18n.MessageService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WompiApiClient {

    /** Default token expiry margin before the token expires. */
    private static final int TOKEN_MARGIN_SECONDS = 30;

    /** Builder used to construct the REST client on startup. */
    private final RestClient.Builder restClientBuilder;

    /** JSON mapper used to parse Wompi API responses. */
    private final ObjectMapper objectMapper;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /** OAuth client identifier. */
    @Value("${wompi.client-id}")
    private String clientId;

    /** OAuth client secret. */
    @Value("${wompi.client-secret}")
    private String clientSecret;

    /** Wompi application identifier. */
    @Value("${wompi.app-id}")
    private String idAplicativo;

    /** REST client configured against the Wompi API. */
    private RestClient restClient;

    /** Cached access token. */
    private volatile String cachedToken;

    /** Instant at which the cached token expires. */
    private volatile Instant tokenExpiry = Instant.EPOCH;

    /**
     * Builds the REST client once all configuration properties have
     * been injected.
     */
    @PostConstruct
    private void init() {
        this.restClient = restClientBuilder
                .baseUrl("https://api.wompi.sv")
                .build();
    }

    /**
     * Retrieve a valid access token from the Wompi identity service.
     *
     * @return a cached or freshly fetched access token
     */
    private synchronized String getAccessToken() {
        if (Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        var response = RestClient.create("https://id.wompi.sv")
            .post()
            .uri("/connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=client_credentials&client_id=%s&client_secret=%s"
                .formatted(clientId, clientSecret))
            .retrieve()
            .body(TokenResponse.class);

        cachedToken = response.accessToken();
        tokenExpiry = Instant.now().plusSeconds(
                (long) response.expiresIn() - TOKEN_MARGIN_SECONDS
        );
        return cachedToken;
    }

    /**
     * Create a recurring payment link for a subscription plan.
     *
     * @param diaDePago the day of month for the charge
     * @param nombre the display name for the link
     * @param monto the amount to charge
     * @param descripcion the product description
     * @return a recurring link response
     */
    public RecurringLinkResponse createRecurringLink(
            final int diaDePago,
            final String nombre,
            final BigDecimal monto,
            final String descripcion
    ) {
        log.info("createRecurringLink usando idAplicativo={}", idAplicativo);

        var body = Map.of(
                "diaDePago", diaDePago,
                "nombre", nombre,
                "idAplicativo", idAplicativo,
                "monto", monto,
                "descripcionProducto", descripcion
        );

        String raw = restClient.post()
                .uri("/EnlacePagoRecurrente")
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        log.info("RAW createRecurringLink: {}", raw);

        JsonNode node;
        try {
            node = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("Wompi response is not valid JSON: {}", raw, e);
            throw new IllegalStateException(
                messageService.get("payment.gateway.invalidResponse"), e
            );
        }

        String id = firstNonNullText(node, "id", "idEnlace");
        String urlSuscribirse = firstNonNullText(
                node,
                "urlCortaSuscribirse", "urlEnlace", "urlLargaSuscribirse"
        );

        if (id == null || urlSuscribirse == null) {
            log.error(
                "Wompi response is missing expected fields (id/url). "
                + "Raw JSON: {}", raw
            );
            throw new IllegalStateException(
                messageService.get("payment.gateway.invalidResponse")
            );
        }

        return new RecurringLinkResponse(id, urlSuscribirse);
    }

    /**
     * Returns the first non-null text value from the provided JSON node.
     *
     * @param node source JSON node
     * @param candidateFields candidate field names
     * @return the first non-null text value, or {@code null} if none exists
     */
    private String firstNonNullText(
        final JsonNode node,
        final String... candidateFields
    ) {
        for (String field : candidateFields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    /**
     * Retrieve subscribers associated with a recurring link.
     *
     * @param idEnlace the recurring link identifier
     * @return the discovered subscribers
     */
    public List<SubscriberInfo> getSubscribers(final String idEnlace) {
        return restClient.get()
            .uri("/EnlacePagoRecurrente/{id}/suscripciones", idEnlace)
            .header("Authorization", "Bearer " + getAccessToken())
            .retrieve()
            .body(new ParameterizedTypeReference<List<SubscriberInfo>>() { });
    }

    /**
     * Deactivate a recurring payment link.
     *
     * @param idEnlace the recurring link identifier
     */
    public void deactivateRecurringLink(final String idEnlace) {
        restClient.post()
                .uri("/EnlacePagoRecurrente/{id}", idEnlace)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Create a one-time payment link.
     *
     * @param identificadorEnlaceComercio the merchant link identifier
     * @param monto the amount to charge
     * @param nombreProducto the product name
     * @return a payment link response
     */
    public PaymentLinkResponse createPaymentLink(
            final String identificadorEnlaceComercio,
            final BigDecimal monto,
            final String nombreProducto
    ) {
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
            long idEnlace,
            String urlQrCodeEnlace,
            String urlEnlace,
            boolean estaProductivo
    ) { }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") int expiresIn
    ) { }

    public record RecurringLinkResponse(
        String id, String urlCortaSuscribirse
    ) { }

    public record SubscriberInfo(
            String id,
            Instant fechaCreacion,
            String alias,
            BigDecimal monto,
            int pagosRealizados,
            String estado,
            String idSuscriptor,
            String nombreSuscriptor,
            Instant fechaInicio,
            Integer diaPago
    ) { }
}
