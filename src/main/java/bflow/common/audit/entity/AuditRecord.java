package bflow.common.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable record of a state-changing request, per ADR-0010 §9
 * (bflow-mcp) — user, actor type, source, action, resource, timestamp,
 * correlation id, and result, for every write the API executes,
 * regardless of which client made it.
 */
@Entity
@Table(name = "audit_records")
@Getter
@Setter
public class AuditRecord {

    /** Unique identifier for the audit record itself. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The authenticated user the request was executed as. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Who is acting: {@code USER} (a person, via the web client),
     * {@code AGENT} (an AI agent, via MCP), or {@code SYSTEM}
     * (internal, scheduled work). Advisory — never authorization input,
     * see {@code AuditFilter}.
     */
    @Column(name = "actor_type", nullable = false)
    private String actorType;

    /** Which interface the request came through: {@code WEB},
     *  {@code MCP}, or {@code INTERNAL}. */
    @Column(nullable = false)
    private String source;

    /** HTTP method of the request, e.g. {@code POST}. */
    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    /** Request path, e.g. {@code /api/v1/expenses/123}. */
    @Column(nullable = false)
    private String path;

    /** Coarse resource type derived from the path, e.g. {@code EXPENSE}. */
    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    /** HTTP status code the request actually completed with. */
    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    /** {@code SUCCESS} or {@code FAILURE}, derived from {@code httpStatus}. */
    @Column(nullable = false)
    private String result;

    /** Caller-supplied correlation id, if any (ADR-0010 §5/§9). */
    @Column(name = "correlation_id")
    private String correlationId;

    /** When this record was written. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}