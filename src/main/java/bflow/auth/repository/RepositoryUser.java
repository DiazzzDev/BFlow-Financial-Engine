package bflow.auth.repository;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link User} entities.
 */
@Repository
public interface RepositoryUser
        extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Finds a user by ID.
     * @param id the user UUID.
     * @return optional user.
     */
    Optional<User> findById(UUID id);

    /**
     * Finds a user by email.
     * @param email the email address.
     * @return optional user.
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by Cognito subject identifier.
     *
     * @param cognitoSub Cognito subject identifier
     * @return optional user
     */
    Optional<User> findByCognitoSub(String cognitoSub);

    List<User> findByStatusAndDeletionRequestedAtBefore(
            UserStatus status, Instant cutoff);
}
