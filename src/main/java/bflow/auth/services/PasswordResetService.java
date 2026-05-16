package bflow.auth.services;

import bflow.auth.DTO.Record.ForgotPasswordRequest;
import bflow.auth.DTO.Record.ResetPasswordRequest;
import bflow.auth.entities.AuthAccount;
import bflow.auth.entities.PasswordResetToken;
import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.PasswordResetTokenRepository;
import bflow.auth.repository.RepositoryAuthAccount;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.utils.PasswordResetTokenProvider;
import bflow.common.aws.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing password reset operations.
 */
@Transactional
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    /** Duration in minutes for password reset token expiration. */
    private static final int RESET_TOKEN_EXPIRATION_MINUTES = 15;
    /** Repository for user data. */
    private final RepositoryUser repositoryUser;
    /** Repository for authentication account data. */
    private final RepositoryAuthAccount repositoryAuthAccount;
    /** Repository for password reset tokens. */
    private final PasswordResetTokenRepository tokenRepository;
    /** Provider for generating and hashing reset tokens. */
    private final PasswordResetTokenProvider tokenProvider;
    /** Password encoder for hashing passwords. */
    private final PasswordEncoder passwordEncoder;
    /** Service for sending email notifications. */
    private final EmailTemplateService emailService;

    /**
     * Initiates the forgot password flow for a user.
     * @param request the forgot password request containing the email.
     */
    public void forgotPassword(final ForgotPasswordRequest request) {

        repositoryUser.findByEmail(request.email())
                .ifPresent(this::handleForgotPassword);
    }

    private void handleForgotPassword(final User user) {

        if (user.getStatus() == UserStatus.DELETED) {
            return;
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            return;
        }

        AuthAccount authAccount = repositoryAuthAccount
                .findByUserIdAndProvider(
                        user.getId(),
                        AuthProvider.LOCAL
                )
                .orElse(null);

        if (authAccount == null) {
            return;
        }

        if (!authAccount.isEnabled()) {
            return;
        }

        if (authAccount.getPasswordHash() == null) {
            return;
        }

        createResetToken(user);
    }

    private void createResetToken(final User user) {

        tokenRepository.deleteByUserId(user.getId());

        String rawToken = tokenProvider.generateRawToken();
        String hash = tokenProvider.hash(rawToken);

        PasswordResetToken token = new PasswordResetToken();

        token.setUserId(user.getId());
        token.setTokenHash(hash);
        token.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(RESET_TOKEN_EXPIRATION_MINUTES)
        );
        token.setUsed(false);

        tokenRepository.save(token);

        emailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getEmail(),
                rawToken
        );
    }

    /**
     * Resets the user password using a valid reset token.
     * @param request the reset password request with token
     *        and new password.
     * @throws IllegalArgumentException if token is invalid, expired,
     *         or already used.
     */
    public void resetPassword(final ResetPasswordRequest request) {

        String hash = tokenProvider.hash(request.token());

        PasswordResetToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid token"));

        if (token.isUsed()) {
            throw new IllegalArgumentException("Token already used");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token expired");
        }

        User user = repositoryUser.findById(token.getUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid token"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Invalid token");
        }

        AuthAccount authAccount = repositoryAuthAccount
                .findByUserIdAndProvider(
                        user.getId(),
                        AuthProvider.LOCAL
                )
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid token"));

        authAccount.setPasswordHash(
                passwordEncoder.encode(request.newPassword())
        );

        tokenRepository.deleteByUserId(user.getId());

        repositoryAuthAccount.save(authAccount);
    }
}
