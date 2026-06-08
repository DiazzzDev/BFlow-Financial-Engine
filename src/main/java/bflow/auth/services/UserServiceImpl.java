package bflow.auth.services;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.AuthAccount;
import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryAuthAccount;
import bflow.auth.repository.RepositoryUser;
import bflow.subscription.repository.RepositoryPlan;
import bflow.subscription.repository.RepositorySubscription;
import bflow.subscription.services.SubscriptionService;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of the {@link UserService}.
 * Handles all user-related business logic and operations.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    /** Roles in access token. */
    private static final String ROLE_USER = "ROLE_USER";

    /** Repository for user core data. */
    private final RepositoryUser userRepository;

    /** Repository for authentication account mapping. */
    private final RepositoryAuthAccount authAccountRepository;

    /** Service for refresh token operations. */
    private final ServiceRefreshToken serviceRefreshToken;

    /** Repository for subscription lookup and persistence. */
    private final RepositorySubscription subscriptionRepository;

    /** Repository for plan lookup and persistence. */
    private final RepositoryPlan repositoryPlan;

    /** Service responsible for subscription lifecycle operations. */
    private final SubscriptionService subscriptionService;

    /**
     * Resolves an OAuth2 user by email, provider ID, and provider type.
     * Handles user validation and creation for OAuth2 authentication.
     * @param email the user's email address.
     * @param providerId the provider-specific user identifier.
     * @param provider the authentication provider.
     * @return the resolved or newly created User entity.
     */
    @Override
    @Transactional
    public User resolveOAuth2User(
            final String email,
            final String providerId,
            final AuthProvider provider,
            final boolean emailVerified
    ) {

        // 1. Check existing OAuth account
        Optional<AuthAccount> existingAccount =
                authAccountRepository.findByProviderAndProviderUserId(
                        provider,
                        providerId
                );

        if (existingAccount.isPresent()) {
            return existingAccount.get().getUser();
        }

        // 2. Check existing user by email
        Optional<User> existingUser =
                userRepository.findByEmail(email);

        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();

            // TRUST GOOGLE AS VERIFICATION SOURCE
            if (emailVerified && !user.isEmailVerified()) {
                user.setEmailVerified(true);
            }

        } else {
            user = createUser(email, emailVerified);
        }

        // 3. persist user state changes (important in JPA)
        user = userRepository.save(user);

        // 4. create OAuth account link
        AuthAccount account = AuthAccount.builder()
                .user(user)
                .provider(provider)
                .providerUserId(providerId)
                .enabled(true)
                .build();

        authAccountRepository.save(account);

        return user;
    }

    private User createUser(
            final String email,
            final boolean emailVerified
    ) {

        User user = User.builder()
                .email(email)
                .emailVerified(emailVerified)
                .roles(Set.of(ROLE_USER))
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        subscriptionService.createFreeSubscription(savedUser);

        return savedUser;
    }

    /**
     * Finds a user by their unique identifier.
     * @param id the user's unique identifier (UUID).
     * @return the User entity.
     * @throws IllegalStateException if the user is not found.
     */
    @Override
    public User findById(final UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    /**
    * Implementation of {@link UserService}.
    *
    * <p>This class is not intended to be extended.
    * It is proxied by Spring for transactional behavior.
    */
    @Override
    public UserProfileResponse updateProfile(
            final UUID userId,
            final UpdateUserProfileRequest request
    ) {

        //Check if user has an active account
        validateUserActive(userId);

        User user = findById(userId);

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim());
        }

        userRepository.save(user);

        return getProfile(userId);
    }

    /**
     * Performs a soft delete of a user account by changing their status.
     * @param userId the unique identifier of the user to delete.
     */
    @Override
    public void softDelete(final UUID userId) {

        //Check if user has an active account
        validateUserActive(userId);

        User user = findById(userId);

        user.setStatus(UserStatus.DELETED);
        serviceRefreshToken.revokeAll(userId);

        userRepository.save(user);
    }

    /**
     * Validates that a user account is active.
     * @param userId the unique identifier of the user.
     * @throws IllegalStateException if the user account is not active.
     */
    public void validateUserActive(final UUID userId) {

        User user = findById(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("User account is not active");
        }
    }

    /**
     * Retrieves the user profile information.
     * @param userId the unique identifier of the user.
     * @return the user profile response.
     */
    @Override
    public UserProfileResponse getProfile(final UUID userId) {

        //Check if user has an active account
        validateUserActive(userId);

        User user = findById(userId);

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .roles(user.getRoles())
                .status(user.getStatus())
                .build();
    }
}
