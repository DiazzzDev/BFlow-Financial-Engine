package bflow.auth.repository;

import bflow.auth.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing password reset tokens.
 */
@Repository
public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Finds a password reset token by its hash.
     * @param tokenHash the token hash.
     * @return an Optional containing the token if found.
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Deletes all password reset tokens for a user.
     * @param userId the user ID.
     */
    void deleteByUserId(UUID userId);

}
