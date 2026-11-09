package bflow.gmail.repository;

import bflow.gmail.entity.GmailImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GmailImportRepository extends JpaRepository<GmailImport, UUID> {
    boolean existsByUserIdAndMessageId(UUID userId, String messageId);
    Optional<GmailImport> findByIdAndUserId(UUID id, UUID userId);
    Optional<GmailImport> findByTextractJobId(String textractJobId);
    List<GmailImport> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Transactional
    @Query("update GmailImport i set i.status = 'PROCESSING', i.updatedAt = :now where i.id = :id and (i.status = 'QUEUED' or (i.status = 'PROCESSING' and i.updatedAt < :expired))")
    int claimQueued(@Param("id") UUID id, @Param("now") java.time.Instant now,
                    @Param("expired") java.time.Instant expired);
}
