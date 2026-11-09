package bflow.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "Request property path. Use this value to bind "
                + "the message to a form control; nested paths may be dotted.",
                example = "date")
        String field,
        @Schema(description = "Stable validation identifier. For Bean "
                + "Validation this is the message key from the request DTO; "
                + "domain validation may use an ErrorCode enum value.",
                example = "transaction.date.futureNotAllowed")
        String code,
        @Schema(description = "Localized, user-facing validation message. "
                + "Do not parse this value to determine behavior.",
                example = "La fecha no puede ser futura.")
        String message
) {
}
