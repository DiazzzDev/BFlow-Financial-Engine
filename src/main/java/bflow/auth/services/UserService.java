package bflow.auth.services;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.User;
import bflow.auth.mapper.UserProfileMapper;
import bflow.auth.enums.NameSource;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.exception.EmailDeliveryException;
import bflow.common.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.mapstruct.factory.Mappers;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of the {@link UserService}.
 * Handles all user-related business logic and operations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class UserService {

    /** Generated mapper for the public profile response. */
    private static final UserProfileMapper PROFILE_MAPPER =
            Mappers.getMapper(UserProfileMapper.class);

    /** Grace period before a pending-deletion account is hard-deleted. */
    private static final int DELETION_GRACE_PERIOD_DAYS = 30;

    /** Repository for user core data. */
    private final RepositoryUser userRepository;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /** Service for sending templated account lifecycle emails. */
    private final EmailTemplateService emailTemplateService;

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
     * Marks a user account as pending deletion. The account keeps its
     * data for {@value #DELETION_GRACE_PERIOD_DAYS} days, during which
     * the user can cancel via {@link #cancelDeletion(UUID)}.
     * @param userId the unique identifier of the user to delete.
     */
    public void softDelete(final UUID userId) {

        //Check if user has an active account
        validateUserActive(userId);

        User user = findById(userId);

        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now());

        userRepository.save(user);

        try {
            emailTemplateService.sendAccountDeletionRequestedEmail(
                    user.getEmail(),
                    user.getName(),
                    user.getDeletionRequestedAt().plus(
                            Duration.ofDays(DELETION_GRACE_PERIOD_DAYS)
                    ),
                    user.getLanguage()
            );
        } catch (EmailDeliveryException ex) {
            log.error("Could not send account-deletion request email "
                    + "to {}", user.getId(), ex);
        }
    }

    /**
     * Cancels a pending deletion, restoring the account to ACTIVE.
     * @param userId the unique identifier of the user.
     */
    public void cancelDeletion(final UUID userId) {

        User user = findById(userId);

        if (user.getStatus() != UserStatus.PENDING_DELETION) {
            throw new IllegalStateException(
                    messageService.get("user.deletion.notPending")
            );
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setDeletionRequestedAt(null);

        userRepository.save(user);

        try {
            emailTemplateService.sendAccountDeletionCancelledEmail(
                    user.getEmail(), user.getName(), user.getLanguage()
            );
        } catch (EmailDeliveryException ex) {
            log.error("Could not send account-deletion cancellation email "
                    + "to {}", user.getId(), ex);
        }
    }

    /**
     * Resolves how many whole days remain before a pending-deletion
     * account is hard-deleted.
     * @param user the user.
     * @return days remaining, floored at 0.
     */
    public long daysRemainingBeforeHardDelete(final User user) {
        if (user.getStatus() != UserStatus.PENDING_DELETION
                || user.getDeletionRequestedAt() == null) {
            return 0;
        }

        Instant scheduledAt = user.getDeletionRequestedAt()
                .plus(Duration.ofDays(DELETION_GRACE_PERIOD_DAYS));

        long days = Duration.between(Instant.now(), scheduledAt).toDays();

        return Math.max(days, 0);
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

        return PROFILE_MAPPER.toResponse(user);
    }
}
