package bflow.subscription.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public record WompiWebhookPayload(
        String idCuenta,
        Instant fechaTransaccion,
        BigDecimal monto,
        String idTransaccion,
        String resultadoTransaccion,
        boolean esProductiva,
        EnlacePagoInfo enlacePago,
        @JsonProperty("cliente") ClienteInfo cliente
) {
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public record EnlacePagoInfo(
            Long id,
            String identificadorEnlaceComercio,
            String nombreProducto
    ) { }

    public record ClienteInfo(
        String nombre,
        String email
    ) { }
}
