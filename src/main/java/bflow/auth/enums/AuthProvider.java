package bflow.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of supported authentication providers.
 */
@Schema(description = "Authentication provider used by an account.",
        allowableValues = {"LOCAL", "GOOGLE"})
public enum AuthProvider {
    /** Local database authentication. */
    LOCAL,

    /** Google OAuth2 authentication. */
    GOOGLE
}
