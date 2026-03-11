package bflow.auth.DTO.user;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for updating user profile information.
 * Contains the fields that can be modified by a user.
 */
@Getter
@Setter
public class UpdateUserProfileRequest {

    /** The new email address for the user. */
    @Email
    private String email;
}
