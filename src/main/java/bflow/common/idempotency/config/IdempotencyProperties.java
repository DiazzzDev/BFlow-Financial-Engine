package bflow.common.idempotency.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the application's idempotency mechanism.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.idempotency")
public class IdempotencyProperties {
    /** Default time-to-live for idempotency records, in hours. */
    private static final long DEFAULT_TTL_HOURS = 24L;

    /** Time-to-live for persisted idempotency records. */
    private Duration ttl = Duration.ofHours(DEFAULT_TTL_HOURS);

    /** Name of the HTTP header carrying the idempotency key. */
    private String headerName = "Idempotency-Key";
}
