package bflow.auth.services;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import bflow.auth.DTO.UserMeResponse;
import bflow.auth.entities.User;
import bflow.auth.enums.NameSource;
import bflow.auth.enums.PictureSource;
import bflow.auth.enums.UserStatus;
import bflow.auth.mapper.UserMapper;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.security.CognitoIdTokenValidator;
import bflow.common.i18n.MessageService;
import bflow.subscription.dto.CurrentSubscriptionResponse;
import bflow.subscription.services.PlanLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronizes Cognito users with the local database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSyncService {

    /**
     * Repository for user persistence.
     */
    private final RepositoryUser repositoryUser;

    /**
     * Service responsible for initial user setup.
     */
    private final AuthBootstrapService authBootstrapService;

    /**
     * Validator for Cognito ID tokens.
     */
    private final CognitoIdTokenValidator idTokenValidator;

    /**
     * Mapper for building user-facing response DTOs.
     */
    private final UserMapper userMapper;

    /**
     * Service used to resolve the user's current plan (code, name,
     * status, feature flags, and limits) for the sync response.
     */
    private final PlanLimitService planLimitService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Synchronizes the authenticated Cognito user with the local database
     * and returns the initial session data required by the client.
     *
     * @param accessJwt validated access token
     * @param request synchronization request
     * @return synchronization result
     */
    @Transactional
    public SyncUserResponse synchronize(
            final Jwt accessJwt,
            final SyncUserRequest request
    ) {
        Jwt idToken = idTokenValidator.validate(request.idToken());

        String sub = idToken.getSubject();
        String email = idToken.getClaimAsString("email");
        Boolean emailVerified = idToken.getClaimAsBoolean("email_verified");
        String name = idToken.getClaimAsString("name");
        String picture = idToken.getClaimAsString("picture");

        if (!accessJwt.getSubject().equals(sub)) {
            throw new IllegalArgumentException(
                    messageService.get("auth.tokenSubjectMismatch")
            );
        }

        Optional<User> existingBySub = repositoryUser.findByCognitoSub(sub);

        if (existingBySub.isPresent()) {
            User user = existingBySub.get();

            applyProfileClaims(user, name, picture);
            repositoryUser.save(user);

            return buildResponse(user, false);
        }

        Optional<User> existingByEmail = repositoryUser.findByEmail(email);

        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();

            user.setCognitoSub(sub);

            if (Boolean.TRUE.equals(emailVerified)) {
                user.setEmailVerified(true);
            }

            applyProfileClaims(user, name, picture);
            repositoryUser.save(user);

            return buildResponse(user, false);
        }

        User newUser = User.builder()
                .cognitoSub(sub)
                .email(email)
                .name(name)
                .pictureUrl(picture)
                .pictureSource(
                        picture != null && !picture.isBlank()
                                ? PictureSource.GOOGLE
                                : PictureSource.NONE
                )
                .status(UserStatus.ACTIVE)
                .emailVerified(Boolean.TRUE.equals(emailVerified))
                .roles(Set.of("ROLE_USER"))
                .build();

        repositoryUser.save(newUser);

        return buildResponse(newUser, true);
    }

    /**
     * Builds the session response returned after authentication
     * synchronization.
     *
     * Self-heals accounts missing their default wallet or free
     * subscription — both {@link AuthBootstrapService#bootstrap}
     * steps are idempotent, so this is safe (and cheap) to call for
     * every sync, not only brand-new users. This matters because
     * bootstrap previously only ran on first-ever registration:
     * any account created another way (pre-dating the subscriptions
     * feature, a data migration, etc.) never got a subscription row
     * and would otherwise be stuck with a {@code null} plan forever.
     *
     * @param user authenticated user
     * @param isNewUser whether the user was created during synchronization
     * @return complete synchronization response
     */
    private SyncUserResponse buildResponse(
            final User user,
            final boolean isNewUser
    ) {
        authBootstrapService.bootstrap(user);

        UserMeResponse meResponse = userMapper.toMeResponse(user);

        return new SyncUserResponse(
                meResponse.id(),
                user.getEmail(),
                meResponse.roles(),
                isNewUser,
                meResponse.subscription(),
                resolveCurrentPlan(user.getId()),
                meResponse.wallets(),
                meResponse.profile()
        );
    }

    /**
     * Resolves the user's current plan for the sync response.
     *
     * Every user gets a free plan on registration (see
     * {@link AuthBootstrapService#bootstrap}) and the free plan can
     * never be canceled, so this should always succeed in practice.
     * It's still wrapped defensively — sync sits on the critical
     * login path, so a plan-resolution edge case (e.g. stale data
     * from a migration) degrades to a {@code null} plan instead of
     * failing the whole sign-in.
     *
     * @param userId the user identifier
     * @return the current plan, or {@code null} if it couldn't be
     *         resolved
     */
    private CurrentSubscriptionResponse resolveCurrentPlan(
            final UUID userId
    ) {
        try {
            return planLimitService.getCurrentSubscriptionInfo(userId);
        } catch (IllegalStateException ex) {
            log.warn(
                    "Could not resolve current plan for user {} during "
                            + "sync: {}",
                    userId,
                    ex.getMessage()
            );
            return null;
        }
    }

    /**
     * Updates the user's name and, if the picture didn't come from an S3
     * upload, their picture URL — sourced from the Cognito ID token.
     *
     * @param user the user to update
     * @param name the display name claim, or {@code null}
     * @param picture the picture URL claim, or {@code null}
     */
    private void applyProfileClaims(
            final User user,
            final String name,
            final String picture
    ) {
        boolean canOverwriteName =
                user.getNameSource() != NameSource.USER;

        if (name != null && !name.isBlank() && canOverwriteName) {
            user.setName(name);
        }

        boolean canOverwritePicture =
                user.getPictureSource() != PictureSource.S3;

        if (picture != null && !picture.isBlank() && canOverwritePicture) {
            user.setPictureUrl(picture);
            user.setPictureSource(PictureSource.GOOGLE);
        }
    }
}
