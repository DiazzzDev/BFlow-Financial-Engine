package bflow.auth.DTO.Record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request record for password reset operation.
 * @param token the password reset token.
 * @param newPassword the new password to set
 *        (must be 12-255 characters).
 */
public record ResetPasswordRequest(
        @NotBlank
        String token,

        @NotBlank
        @Size(min = PasswordConstraints.MIN_PASSWORD_LENGTH,
                max = PasswordConstraints.MAX_PASSWORD_LENGTH,
                message = "New password must be between"
                        + " 12 to 255 characters")
        String newPassword
) {
}
