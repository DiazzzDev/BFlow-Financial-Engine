package bflow.common.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores idempotency keys for financial write operations to prevent
 * duplicate processing on client retries.
 */
@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"idempotency_key", "user_id", "endpoint"}
        )
)
@Getter
@Setter
public class IdempotencyRecord {

    /** Unique identifier for the idempotency record. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Client-provided idempotency key. */
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    /** Identifier of the authenticated user who owns the request. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** API endpoint associated with the idempotency key. */
    @Column(nullable = false)
    private String endpoint;

    /** Cached JSON response returned for the original request. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    /** SHA-256 hash of the normalized request body. */
    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    /** HTTP status code returned by the original request. */
    @Column(nullable = false)
    private int statusCode;

    /** Timestamp when this record was created. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp after which this record is considered expired and
     * eligible for cleanup. Also used to decide whether a matching
     * key should be treated as stale (i.e. safe to reprocess).
     */
    @Column(nullable = false)
    private Instant expiresAt;
}
