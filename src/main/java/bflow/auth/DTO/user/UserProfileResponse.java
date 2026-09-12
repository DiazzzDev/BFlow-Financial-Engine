package bflow.auth.DTO.user;

import bflow.auth.enums.SupportedLanguage;
import bflow.auth.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

/**
 * DTO for user profile response information.
 * Contains the publicly viewable user profile data.
 */
@Getter
@Builder
public class UserProfileResponse {

    /** The user's unique identifier. */
    private UUID id;

    /** The user's email address. */
    private String email;

    /** The user's display name. */
    private String name;

    /** The URL of the user's profile picture. */
    private String pictureUrl;

    /** The user's assigned roles. */
    private Set<String> roles;

    /** The user's current account status. */
    private UserStatus status;

    /** The user's preferred language. */
    private SupportedLanguage language;
}
