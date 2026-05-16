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

@Transactional
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final RepositoryUser repositoryUser;
    private final RepositoryAuthAccount repositoryAuthAccount;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailTemplateService emailService;

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
                LocalDateTime.now().plusMinutes(15)
        );
        token.setUsed(false);

        tokenRepository.save(token);

        emailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getEmail(), //TODO: Add user name to improve UX
                rawToken
        );
    }

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
