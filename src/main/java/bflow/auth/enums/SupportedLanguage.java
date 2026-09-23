package bflow.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Languages the backend has translations for. Kept separate from
 * {@code Locale} so an invalid value in a request body fails
 * deserialization with a clear 400 (via the existing
 * {@code HttpMessageNotReadableException} handling) rather than
 * silently accepting an arbitrary language string.
 */
@Schema(description = "Language supported by the API.",
        allowableValues = {"EN", "ES"})
public enum SupportedLanguage {
    EN,
    ES
}
