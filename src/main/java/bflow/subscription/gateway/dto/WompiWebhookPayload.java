package bflow.subscription.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public record WompiWebhookPayload(
        String idCuenta,
        Instant fechaTransaccion,
        BigDecimal monto,
        String idTransaccion,
        String resultadoTransaccion,
        boolean esProductiva,
        EnlacePagoInfo enlacePago,
        ClienteInfo cliente
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public record EnlacePagoInfo(
            String id,
            String identificadorEnlaceComercio,
            String nombreProducto,
            String descripcionProducto
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public record ClienteInfo(
            String nombre,
            @JsonProperty("EMail") String email,
            String idSuscripcion,
            String diaPagoSuscripcion
    ) { }
}