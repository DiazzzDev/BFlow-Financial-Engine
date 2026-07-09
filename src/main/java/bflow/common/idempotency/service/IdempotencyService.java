package bflow.common.idempotency.service;

import bflow.common.idempotency.config.IdempotencyProperties;
import bflow.common.idempotency.entity.IdempotencyRecord;
import bflow.common.idempotency.repository.RepositoryIdempotency;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    /** Repository for idempotency records. */
    private final RepositoryIdempotency repositoryIdempotency;

    /** Configuration properties for idempotency handling. */
    private final IdempotencyProperties properties;

    /**
    * Finds an existing idempotency record.
    *
    * @param key idempotency key provided by the client
    * @param userId authenticated user identifier
    * @param endpoint protected endpoint
    * @return matching idempotency record, if present
    */
    public Optional<IdempotencyRecord> find(
            final String key, final UUID userId, final String endpoint
    ) {
        return repositoryIdempotency
                .findByIdempotencyKeyAndUserIdAndEndpoint(
                    key, userId, endpoint
                );
    }

    /**
    * Computes the SHA-256 hash of the request body.
    *
    * @param body request body bytes
    * @return hexadecimal SHA-256 hash
    */
    public String hashBody(final byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
    * Persists a successful idempotent response.
    *
    * @param key client-provided idempotency key
    * @param userId authenticated user identifier
    * @param endpoint protected endpoint
    * @param requestHash SHA-256 hash of the request body
    * @param statusCode HTTP status returned by the request
    * @param responseBody serialized response body
    */
    @Transactional
    public void save(
            final String key, final UUID userId, final String endpoint,
            final String requestHash, final int statusCode,
            final byte[] responseBody
    ) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(key);
        record.setUserId(userId);
        record.setEndpoint(endpoint);
        record.setRequestHash(requestHash);
        record.setStatusCode(statusCode);
        record.setResponseBody(
            new String(responseBody, StandardCharsets.UTF_8)
        );
        record.setExpiresAt(Instant.now().plus(properties.getTtl()));

        try {
            repositoryIdempotency.save(record);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent request already persisted the same key.
        }
    }
}
