package bflow.common.idempotency.repository;

import bflow.common.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing idempotency records used to prevent duplicate
 * processing of requests with the same idempotency key.
 */
@Repository
public interface RepositoryIdempotency
    extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Finds an idempotency record by key, user and endpoint.
     *
     * @param idempotencyKey unique request key
     * @param userId user identifier
     * @param endpoint API endpoint
     * @return existing idempotency record if present
     */
    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserIdAndEndpoint(
            String idempotencyKey, UUID userId, String endpoint
    );

    /**
     * Deletes expired idempotency records.
     *
     * @param now current timestamp used as cutoff
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    int deleteExpired(Instant now);
}
