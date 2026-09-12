package bflow.auth.enums;

/**
 * Languages the backend has translations for. Kept separate from
 * {@code Locale} so an invalid value in a request body fails
 * deserialization with a clear 400 (via the existing
 * {@code HttpMessageNotReadableException} handling) rather than
 * silently accepting an arbitrary language string.
 */
public enum SupportedLanguage {
    EN,
    ES
}
