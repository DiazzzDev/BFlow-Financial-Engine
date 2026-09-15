package bflow.common.audit.repository;

import bflow.common.audit.entity.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for audit records (ADR-0010 §9).
 */
@Repository
public interface RepositoryAudit extends JpaRepository<AuditRecord, UUID> {

    /**
     * Lists a user's own audit records, most recent first — the read
     * path a future "show me what changed" feature (web or MCP) would
     * use. No such feature exists yet; this exists so the write side
     * isn't shipped without a way to ever query it.
     *
     * @param userId the user whose records to list.
     * @return their audit records, most recent first.
     */
    @Query("SELECT a FROM AuditRecord a WHERE a.userId = :userId "
            + "ORDER BY a.createdAt DESC")
    List<AuditRecord> findByUserIdOrderByCreatedAtDesc(UUID userId);
}