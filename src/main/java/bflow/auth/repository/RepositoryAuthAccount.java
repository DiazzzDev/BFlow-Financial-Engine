package bflow.auth.repository;

import bflow.auth.entities.AuthAccount;
import bflow.auth.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Finds an authentication account by provider and provider user ID.
     * @param provider the authentication provider.
     * @param providerUserId the provider-specific user identifier.
     * @return an Optional containing the AuthAccount if found.
     */
    Optional<AuthAccount> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

}
