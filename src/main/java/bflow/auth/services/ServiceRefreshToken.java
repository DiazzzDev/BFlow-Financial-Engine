package bflow.auth.services;

import bflow.auth.DTO.Record.RefreshRotationResult;
import bflow.auth.DTO.Record.RefreshSession;
import bflow.auth.entities.RefreshToken;
import bflow.auth.repository.RepositoryRefreshToken;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing refresh token lifecycle and security rotation.
 */
@Service
@RequiredArgsConstructor
public final class ServiceRefreshToken {

    /** Time-to-live for refresh tokens (14 days). */
    private static final Duration TTL = Duration.ofDays(14);

    /** Repository for token persistence. */
    private final RepositoryRefreshToken repository;

    /**
     * Creates and persists a new refresh token.
     * @param userId the owner of the token.
     * @param rawToken the plain text token to be hashed.
     */
    public void create(final UUID userId, final String rawToken) {
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUserId(userId);
        rt.setTokenHash(hash(rawToken));
        rt.setCreatedAt(Instant.now());
        rt.setExpiresAt(Instant.now().plus(TTL));
        rt.setRevoked(false);

        repository.save(rt);
    }

    /**
     * Revokes a refresh token if it exists.
     * This operation is used during logout flows
     * to invalidate the current session token.
     *
     * @param rawToken the plain text refresh token.
     */
    public void revoke(final String rawToken) {

        String hash = hash(rawToken);

        repository.findByTokenHash(hash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    repository.save(token);
                });
    }

    /**
     * Validates a token without revoking it.
     * @param rawToken the plain text token.
     * @return the RefreshToken entity.
     */
    public RefreshToken validate(final String rawToken) {
        String hash = hash(rawToken);

        RefreshToken token = repository.findByTokenHash(hash)
                .orElseThrow(() ->
                        new SecurityException("Invalid refresh token"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new SecurityException("Invalid refresh token");
        }

        return token;
    }

    /**
     * Performs a full token rotation, issuing a new token.
     * @param rawToken the old plain text token.
     * @return the result containing user ID and the new raw token.
     */
    public RefreshRotationResult rotate(final String rawToken) {
        String hash = hash(rawToken);

        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() ->
                        new SecurityException("Invalid refresh token"));

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            revokeAll(existing.getUserId());
            throw new SecurityException("Expired refresh token");
        }

        if (existing.isRevoked()) {
            revokeAll(existing.getUserId());
            throw new SecurityException("Refresh token reuse detected");
        }

        existing.setRevoked(true);

        String newRawToken = UUID.randomUUID().toString();
        RefreshToken replacement = new RefreshToken();
        replacement.setId(UUID.randomUUID());
        replacement.setUserId(existing.getUserId());
        replacement.setTokenHash(hash(newRawToken));
        replacement.setCreatedAt(Instant.now());
        replacement.setExpiresAt(Instant.now().plus(TTL));

        existing.setReplacedBy(replacement.getId());

        repository.save(existing);
        repository.save(replacement);

        return new RefreshRotationResult(existing.getUserId(), newRawToken);
    }

    /**
     * Lists all active (non-expired, non-revoked) sessions for a user.
     * @param userId the user ID.
     * @param currentTokenId the ID of the token currently in use.
     * @return list of active sessions.
     */
    public List<RefreshSession> listActiveSessions(
            final UUID userId,
            final UUID currentTokenId
    ) {
        return repository
                .findAllByUserIdAndRevokedFalseAndExpiresAtAfter(
                        userId, Instant.now())
                .stream()
                .map(rt -> new RefreshSession(
                        rt.getId(),
                        rt.getCreatedAt(),
                        rt.getExpiresAt(),
                        rt.getId().equals(currentTokenId)
                ))
                .toList();
    }

    /**
     * Revokes all active tokens for a specific user (Security Nuclear Option).
     * @param userId the user ID.
     */
    public void revokeAll(final UUID userId) {
        repository.findAllByUserIdAndRevokedFalse(userId)
                .forEach(rt -> {
                    rt.setRevoked(true);
                    repository.save(rt);
                });
    }

    /**
     * Internal helper to hash tokens.
     * @param token raw token string.
     * @return SHA-256 hash.
     */
    private String hash(final String token) {
        return DigestUtils.sha256Hex(token);
    }
}
