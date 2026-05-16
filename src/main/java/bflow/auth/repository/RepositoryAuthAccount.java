package bflow.auth.repository;

import bflow.auth.entities.AuthAccount;
import bflow.auth.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link AuthAccount} entities.
 */
@Repository
public interface RepositoryAuthAccount
        extends JpaRepository<AuthAccount, UUID> {

    /**
     * Finds an active account by email and provider.
     * @param login the user email.
     * @param provider the auth provider.
     * @return an optional containing the account if found.
     */
    @Query("""
        SELECT a
        FROM AuthAccount a
        JOIN FETCH a.user u
        WHERE u.email = :login
          AND a.provider = :provider
          AND a.enabled = true
    """)
    Optional<AuthAccount> findActiveByLoginAndProvider(
            @Param("login") String login,
            @Param("provider") AuthProvider provider
    );

    /**
     * Checks if an authentication account exists for the given provider
     * and provider user ID.
     * @param provider the authentication provider.
     * @param providerUserId the provider-specific user identifier.
     * @return true if the account exists, false otherwise.
     */
    boolean existsByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    /**
     * Finds an authentication account by provider and provider user ID.
     * @param provider the authentication provider.
     * @param providerUserId the provider-specific user identifier.
     * @return an Optional containing the AuthAccount if found.
     */
    Optional<AuthAccount> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    /**
     * Finds an authentication account by user ID and provider.
     * @param userId the user ID.
     * @param provider the authentication provider.
     * @return an Optional containing the AuthAccount if found.
     */
    Optional<AuthAccount> findByUserIdAndProvider(
            UUID userId,
            AuthProvider provider
    );
}
