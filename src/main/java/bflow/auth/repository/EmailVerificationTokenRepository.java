package bflow.auth.repository;

import bflow.auth.entities.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {
    /**
     * Finds a verification token by its hash.
     * @param hash the token hash to search for.
     * @return an Optional containing the token if found.
     */
    Optional<EmailVerificationToken> findByTokenHash(String hash);

    /**
     * Deletes all verification tokens for a specific user.
     * @param userId the ID of the user whose tokens should be deleted.
     */
    void deleteByUserId(UUID userId);
}
