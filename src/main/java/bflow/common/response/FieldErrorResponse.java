package bflow.common.response;

/**
 * A single field-level validation error, returned inside
 * {@link ApiResponse#getErrors()} when {@code code} is
 * {@link ErrorCode#VALIDATION_ERROR}.
 *
 * @param field the name of the request field that failed validation.
 * @param code a stable, machine-readable code for this specific
 *             failure (e.g. {@code CATEGORY_NAME_REQUIRED}) —
 *             not an {@link ErrorCode} constant, since these are
 *             domain/field-specific and would otherwise bloat that
 *             enum with one entry per validation rule.
 * @param message a human-readable, already-localized message for
 *                this field.
 */
public record FieldErrorResponse(
        String field,
        String code,
        String message
) {
}