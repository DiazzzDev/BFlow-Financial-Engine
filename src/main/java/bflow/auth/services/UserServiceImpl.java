package bflow.auth.services;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.AuthAccount;
import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryAuthAccount;
import bflow.auth.repository.RepositoryUser;
import jakarta.transaction.Transactional;
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

    /** Repository for user core data. */
    private final RepositoryUser userRepository;

    /** Repository for authentication account mapping. */
    private final RepositoryAuthAccount authAccountRepository;

    /**
     * Resolves an OAuth2 user by email, provider ID, and provider type.
     * Handles user validation and creation for OAuth2 authentication.
     * @param email the user's email address.
     * @param providerId the provider-specific user identifier.
     * @param provider the authentication provider.
     * @return the resolved or newly created User entity.
     */
    @Override
    public User resolveOAuth2User(
            final String email,
            final String providerId,
            final AuthProvider provider
    ) {
        return userRepository.findByEmail(email)
                .map(existingUser -> validateProvider(existingUser, provider))
                .orElseGet(() -> createOAuth2User(email, providerId, provider));
    }

    /**
     * Ensures the user is using the same provider they registered with.
     * @param user current user.
     * @param provider attempted provider.
     * @return the validated user.
     */
    private User validateProvider(
            final User user,
            final AuthProvider provider
    ) {

        if (user.getProvider() != provider) {
            throw new IllegalStateException(
                    "User already registered with provider: "
                            + user.getProvider()
            );
        }
        return user;
    }

    private User createOAuth2User(
            final String email,
            final String providerId,
            final AuthProvider provider
    ) {
        User user = User.builder()
                .email(email)
                .status(UserStatus.ACTIVE)
                .provider(provider)
                .roles(Set.of("ROLE_USER"))
                .build();

        userRepository.save(user);

        AuthAccount account = AuthAccount.builder()
                .user(user)
                .provider(provider)
                .providerUserId(providerId)
                .build();

        authAccountRepository.save(account);

        return user;
    }

    /**
     * Finds an existing OAuth2 user by email or creates a new one.
     * This method ensures that a user account exists in the system.
     * @param email the user's email address.
     * @param provider the authentication provider.
     * @return the existing or newly created User entity.
     */
    @Override
    public User findOrCreateOAuthUser(
            final String email,
            final AuthProvider provider
    ) {

        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(email)
                                .provider(provider)
                                .roles(Set.of("ROLE_USER"))
                                .status(UserStatus.ACTIVE)
                                .build()
                ));
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
