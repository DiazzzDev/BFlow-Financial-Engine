package bflow.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Origin of the user's display name. Determines whether the Cognito
 * sync flow is allowed to overwrite it.
 */
@Schema(description = "Origin of the user's display name.",
        allowableValues = {"GOOGLE", "USER"})
public enum NameSource {
    /** Name sourced from Google's OAuth profile claim. */
    GOOGLE,
    /** Name manually set by the user via {@code PATCH /me}. */
    USER
}
