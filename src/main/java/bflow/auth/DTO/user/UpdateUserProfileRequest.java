package bflow.auth.DTO.user;

import bflow.auth.enums.SupportedLanguage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for updating user profile information.
 * Contains the fields that can be modified by a user.
 */
@Getter
@Setter
public class UpdateUserProfileRequest {

    /** Maximum allowed length for the display name. */
    private static final int MAX_NAME_LENGTH = 120;

    /** The new email address for the user. */
    @Email(message = "{user.email.invalid}")
    private String email;

    /** The new display name for the user. */
    @Size(max = MAX_NAME_LENGTH, message = "{user.name.size}")
    private String name;

    /** The user's language preference for messages and emails. */
    private SupportedLanguage language;
}
