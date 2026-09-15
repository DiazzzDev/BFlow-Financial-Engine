package bflow.common.audit.service;

import bflow.common.audit.entity.AuditRecord;
import bflow.common.audit.repository.RepositoryAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes audit records (ADR-0010 §9). Called from {@code AuditFilter}
 * after a state-changing request completes — never from application
 * services directly, so recording an audit entry can never be skipped
 * by a call site forgetting to do it.
 */
@Service
@RequiredArgsConstructor
public class ServiceAudit {

    /** Minimum HTTP status code still considered successful. */
    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 400;

    /** Persistence for audit records. */
    private final RepositoryAudit repositoryAudit;

    /**
     * Records one audit entry.
     *
     * @param userId the authenticated user the request executed as.
     * @param actorType {@code USER}, {@code AGENT}, or {@code SYSTEM}.
     * @param source {@code WEB}, {@code MCP}, or {@code INTERNAL}.
     * @param httpMethod the request's HTTP method.
     * @param path the request path.
     * @param resourceType a coarse resource name derived from the path.
     * @param httpStatus the status the request completed with.
     * @param correlationId caller-supplied correlation id, if any.
     */
    public void record(final UUID userId, final String actorType,
                       final String source, final String httpMethod, final String path,
                       final String resourceType, final int httpStatus,
                       final String correlationId) {

        AuditRecord record = new AuditRecord();
        record.setUserId(userId);
        record.setActorType(actorType);
        record.setSource(source);
        record.setHttpMethod(httpMethod);
        record.setPath(path);
        record.setResourceType(resourceType);
        record.setHttpStatus(httpStatus);
        record.setResult(httpStatus < HTTP_SUCCESS_MAX_EXCLUSIVE
                ? "SUCCESS" : "FAILURE");
        record.setCorrelationId(correlationId);

        repositoryAudit.save(record);
    }
}