package bflow.auth.utils;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for generating and hashing password reset tokens.
 */
@Component
public final class PasswordResetTokenProvider {

    /** Token byte array length for secure random generation. */
    private static final int TOKEN_BYTE_LENGTH = 32;

    /** Generator for cryptographically secure random values. */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a raw password reset token.
     * @return a randomly generated token string.
     */
    public String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Hashes a password reset token using SHA-256.
     * @param token the token to hash.
     * @return the hashed token.
     * @throws IllegalStateException if SHA-256 algorithm is not available.
     */
    public String hash(final String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
