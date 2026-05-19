package bflow.auth.services;

import bflow.auth.entities.User;

import java.util.UUID;

public interface EmailVerificationService {

    /**
     * Sends a verification email to the specified user.
     * @param user the user to send the verification email to.
     */
    void sendVerificationEmail(User user);

    /**
     * Verifies the user's email using the provided token.
     * @param token the verification token.
     */
    void verifyEmail(String token);

    /**
     * Resends the verification email to the user.
     * @param userId the ID of the user to resend the verification to.
     */
    void resendVerification(UUID userId);
}
