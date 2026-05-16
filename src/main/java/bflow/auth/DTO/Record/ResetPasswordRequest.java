package bflow.auth.DTO.Record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank
        String token,

        @NotBlank
        @Size(min = 12, max = 255,
                message = "New password must be between 12 to 255 characters")
        String newPassword
) {
}
