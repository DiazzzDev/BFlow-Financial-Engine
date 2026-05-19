package bflow.auth.services;

import bflow.auth.entities.EmailVerificationToken;
import bflow.auth.entities.User;
import bflow.auth.repository.EmailVerificationTokenRepository;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.utils.SecureTokenProvider;
import bflow.common.aws.service.EmailTemplateService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationServiceImpl implements EmailVerificationService {
    /**
     * Verification token expiration time in hours.
     */
    @Value("${security.email-verification.expiration-hours}")
    private Integer verificationExpirationHours;

    /**
     * Repository for email verification tokens.
     */
    private final EmailVerificationTokenRepository tokenRepository;

    /**
     * Repository for users.
     */
    private final RepositoryUser userRepository;

    /**
     * Provider for secure token generation and hashing.
     */
    private final SecureTokenProvider tokenProvider;

    /**
     * Service for email templates and SES delivery.
     */
    private final EmailTemplateService emailTemplateService;

    /**
     * Sends a verification email to the user.
     * @param user the target user.
     */
    @Override
    public void sendVerificationEmail(final User user) {

        if (user.isEmailVerified()) {
            return;
        }

        tokenRepository.deleteByUser_Id(user.getId());

        String rawToken = tokenProvider.generateRawToken();
        String hash = tokenProvider.hash(rawToken);

        EmailVerificationToken token =
                new EmailVerificationToken();

        token.setUser(user);
        token.setTokenHash(hash);
        token.setExpiresAt(
                LocalDateTime.now()
                        .plusHours(verificationExpirationHours)
        );

        tokenRepository.save(token);

        emailTemplateService.sendEmailVerificationEmail(
                user.getEmail(),
                user.getEmail(),
                rawToken
        );
    }

    /**
     * Verifies a user's email using a verification token.
     * @param rawToken the raw token received from frontend.
     */
    @Override
    public void verifyEmail(final String rawToken) {

        String hash = tokenProvider.hash(rawToken);

        EmailVerificationToken token = tokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid token"
                        )
                );

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Token expired"
            );
        }

        User user = token.getUser();
        user.setEmailVerified(true);

        userRepository.save(user);
        tokenRepository.deleteByUser_Id(user.getId());
    }

    /**
     * Resends a verification email to the user.
     * @param userId the user identifier.
     */
    @Override
    public void resendVerification(final UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );
        if (user.isEmailVerified()) {
            return;
        }

        sendVerificationEmail(user);
    }
}
