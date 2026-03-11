package bflow.auth.services;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import java.util.UUID;

/**
 * Service interface for managing user profiles and OAuth resolution.
 */
public interface UserService {
    /**
     * Finds a user by email or creates a new one for OAuth providers.
     * @param email user email.
     * @param authProvider provider type.
     * @return the existing or new User.
     */
    User findOrCreateOAuthUser(String email, AuthProvider authProvider);

    /**
     * Retrieves a user by their unique identifier.
     * @param id user UUID.
     * @return the found user.
     */
    User findById(UUID id);

    /**
     * Resolves an OAuth2 user by checking email and provider consistency.
     * @param email user email.
     * @param providerId external provider ID.
     * @param provider the provider (e.g., GOOGLE).
     * @return the resolved User entity.
     */
    User resolveOAuth2User(
            String email,
            String providerId,
            AuthProvider provider
    );

    /**
     * Retrieves the user profile for a given user ID.
     * @param userId the unique identifier of the user.
     * @return the user profile response.
     */
    UserProfileResponse getProfile(UUID userId);

    /**
     * Updates the user profile with new information.
     * @param userId the unique identifier of the user.
     * @param request the update profile request containing new data.
     * @return the updated user profile response.
     */
    UserProfileResponse updateProfile(
            UUID userId,
            UpdateUserProfileRequest request
    );

    /**
     * Performs a soft delete of a user account (soft delete).
     * @param userId the unique identifier of the user.
     */
    void softDelete(UUID userId);
}
