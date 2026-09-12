package bflow.auth.services;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.User;
import bflow.auth.enums.NameSource;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import java.util.UUID;

import bflow.common.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the {@link UserService}.
 * Handles all user-related business logic and operations.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    /** Repository for user core data. */
    private final RepositoryUser userRepository;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Finds a user by their unique identifier.
     * @param id the user's unique identifier (UUID).
     * @return the User entity.
     * @throws IllegalStateException if the user is not found.
     */
    public User findById(final UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        messageService.get("user.notFound")));
    }

    /**
     * Updates the profile information of an active user.
     *
     * @param userId the unique identifier of the user to update.
     * @param request the profile update request containing the new information.
     * @return the updated user profile response.
     */
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

        if (request.getName() != null) {
            user.setName(request.getName().trim());
            user.setNameSource(NameSource.USER);
        }

        if (request.getLanguage() != null) {
            user.setLanguage(request.getLanguage());
        }

        userRepository.save(user);

        return getProfile(userId);
    }

    /**
     * Performs a soft delete of a user account by changing their status.
     * @param userId the unique identifier of the user to delete.
     */
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
            throw new IllegalStateException(
                    messageService.get("user.accountNotActive")
            );
        }
    }

    /**
     * Retrieves the user profile information.
     * @param userId the unique identifier of the user.
     * @return the user profile response.
     */
    public UserProfileResponse getProfile(final UUID userId) {

        //Check if user has an active account
        validateUserActive(userId);

        User user = findById(userId);

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .pictureUrl(user.getPictureUrl())
                .roles(user.getRoles())
                .status(user.getStatus())
                .language(user.getLanguage())
                .build();
    }
}
