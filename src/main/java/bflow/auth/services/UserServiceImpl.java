package bflow.auth.services;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import java.util.UUID;
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
public class UserServiceImpl implements UserService {

    /** Repository for user core data. */
    private final RepositoryUser userRepository;

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
