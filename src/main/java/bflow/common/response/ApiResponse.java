package bflow.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * A generic wrapper for all API responses.
 *
 * <p>{@code code} and {@code errors} are additive fields on top of the
 * original {@code success}/{@code message}/{@code data}/{@code timestamp}/
 * {@code path} contract — both are {@code null}-omitted (see
 * {@link JsonInclude}) when unset, so every existing caller of
 * {@link #success} or the two-argument {@link #error} keeps producing
 * byte-for-byte the same JSON shape the current frontend already
 * consumes. New call sites that want a stable error code and/or
 * field-level validation errors use the new overloads below instead.</p>
 *
 * @param <T> the type of the data payload.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {

    /** Indicates if the operation was successful. */
    private final boolean success;
    /** The response message. */
    private final String message;
    /** The data payload. */
    private final T data;
    /** The time the response was generated. */
    private final Instant timestamp;
    /** The request path. */
    private final String path;
    /**
     * Stable, machine-readable error code. {@code null} for success
     * responses and for the legacy two-argument {@link #error} calls
     * that predate this field.
     */
    private final ErrorCode code;
    /**
     * Field-level validation errors, populated only when
     * {@code code} is {@link ErrorCode#VALIDATION_ERROR}.
     */
    private final List<FieldErrorResponse> errors;

    /**
     * Private constructor for ApiResponse.
     * @param aSuccess indicates success.
     * @param aMessage description message.
     * @param aData data payload.
     * @param aTimestamp creation time.
     * @param aPath request path.
     * @param aCode stable error code, or {@code null}.
     * @param aErrors field-level validation errors, or {@code null}.
     */
    private ApiResponse(
            final boolean aSuccess,
            final String aMessage,
            final T aData,
            final Instant aTimestamp,
            final String aPath,
            final ErrorCode aCode,
            final List<FieldErrorResponse> aErrors
    ) {
        this.success = aSuccess;
        this.message = aMessage;
        this.data = aData;
        this.timestamp = aTimestamp;
        this.path = aPath;
        this.code = aCode;
        this.errors = aErrors;
    }

    /**
     * Creates a success response.
     * @param <T> the data type.
     * @param message the success message.
     * @param data the payload.
     * @param path the request path.
     * @return a success ApiResponse.
     */
    public static <T> ApiResponse<T> success(
            final String message,
            final T data,
            final String path
    ) {
        return new ApiResponse<>(
                true, message, data, Instant.now(), path, null, null
        );
    }

    /**
     * Creates an error response. Kept for backward compatibility with
     * every existing call site — produces the exact same JSON shape
     * as before this class gained {@code code}/{@code errors}.
     * @param <T> the data type.
     * @param message the error message.
     * @param path the request path.
     * @return an error ApiResponse.
     */
    public static <T> ApiResponse<T> error(
            final String message,
            final String path
    ) {
        return new ApiResponse<>(
                false, message, null, Instant.now(), path, null, null
        );
    }

    /**
     * Creates an error response carrying a stable, machine-readable
     * code alongside the human-readable message.
     * @param <T> the data type.
     * @param message the error message.
     * @param path the request path.
     * @param code the stable error code.
     * @return an error ApiResponse.
     */
    public static <T> ApiResponse<T> error(
            final String message,
            final String path,
            final ErrorCode code
    ) {
        return new ApiResponse<>(
                false, message, null, Instant.now(), path, code, null
        );
    }

    /**
     * Creates a {@link ErrorCode#VALIDATION_ERROR} response with
     * field-level detail.
     * @param <T> the data type.
     * @param message the top-level validation error message.
     * @param path the request path.
     * @param errors the per-field validation failures.
     * @return a validation-error ApiResponse.
     */
    public static <T> ApiResponse<T> validationError(
            final String message,
            final String path,
            final List<FieldErrorResponse> errors
    ) {
        return new ApiResponse<>(
                false, message, null, Instant.now(), path,
                ErrorCode.VALIDATION_ERROR, errors
        );
    }
}
