package bflow.common.exception;

import bflow.common.idempotency.exception.IdempotencyConflictException;
import bflow.common.response.ApiResponse;
import bflow.common.response.ErrorCode;
import bflow.common.response.FieldErrorResponse;
import bflow.legal.exception.LegalDocumentNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolation;
import org.springframework.validation.FieldError;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.converter.HttpMessageNotReadableException;

import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Global controller advice to handle application-wide exceptions.
 *
 * <p>Every handler that maps to a domain concept now attaches a stable
 * {@link ErrorCode} via the additive {@code ApiResponse.error(message,
 * path, code)} overload — the {@code message} text is untouched, so
 * the current frontend's {@code error.message} usage keeps working
 * byte-for-byte. Handlers for pure HTTP/framework mechanics with no
 * clear domain meaning (405, 406, 415, client disconnects, gateway
 * errors, DB-inconsistency 409s) intentionally keep the two-argument
 * {@code error(message, path)} call — inventing a code for those would
 * be exactly the "blindly add every possible code" anti-pattern this
 * contract is meant to avoid.</p>
 */
@Slf4j
@RestControllerAdvice
public final class GlobalExceptionHandler {

    /**
     * Handles IllegalStateExceptions (e.g., conflicts).
     * @param ex the exception.
     * @param request the current request.
     * @return error response with CONFLICT status.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(
            final IllegalStateException ex,
            final HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.CONFLICT)
                );
    }

    /**
     * Handles authentication credential failures.
     * @param ex the exception.
     * @param request the current request.
     * @return error response with UNAUTHORIZED status.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
            final InvalidCredentialsException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.INVALID_CREDENTIALS
                ));
    }

    /**
     * Handles resource not found exceptions.
     * @param ex the exception.
     * @param request the current request.
     * @return error response with NOT_FOUND status.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            final NotFoundException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
    }

    /**
     * Handles IllegalArgumentExceptions that represent missing resources.
     * Treated as 404 Not Found per convention.
     * @param ex the exception.
     * @param request the current request.
     * @return error response with NOT_FOUND status.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            final IllegalArgumentException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
    }

    /**
     * Handles access denied exceptions (permission violations).
     * @param ex the exception.
     * @param request the current request.
     * @return error response with FORBIDDEN status.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            final AccessDeniedException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : "Access denied",
                        request.getRequestURI(),
                        ErrorCode.FORBIDDEN
                ));
    }

    /**
     * Handles wallet access denied exceptions (wallet-specific permission
     * violations).
     * @param ex the exception.
     * @param request the current request.
     * @return error response with FORBIDDEN status.
     */
    @ExceptionHandler(WalletAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletAccessDenied(
            final WalletAccessDeniedException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.WALLET_ACCESS_DENIED
                ));
    }

    /**
     * Handles file access denied exceptions (file-specific
     * permission violations).
     * @param ex the exception.
     * @param request the current request.
     * @return error response with FORBIDDEN status.
     */
    @ExceptionHandler(FileAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileAccessDenied(
            final FileAccessDeniedException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.FILE_ACCESS_DENIED
                ));
    }

    /**
     * Handles bean validation errors. Now returns per-field detail via
     * {@link ApiResponse#validationError} in addition to the same
     * joined-string {@code message} the frontend already reads — the
     * field {@code code} here is the raw Bean Validation constraint
     * name (e.g. "NotBlank") until DTOs migrate to domain-specific
     * message keys.
     * @param ex the exception.
     * @param request the current request.
     * @return error response with BAD_REQUEST status.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            final MethodArgumentNotValidException ex,
            final HttpServletRequest request) {

        String errorMsg = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": "
                        + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        List<FieldErrorResponse> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new FieldErrorResponse(
                        err.getField(),
                        extractCode(err),
                        err.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(
                        errorMsg, request.getRequestURI(), fieldErrors
                ));
    }

    /**
     * Handles client disconnects and aborted HTTP connections.
     * These are common in production and should not be treated
     * as server-side failures.
     *
     * @param ex the exception.
     * @param request the current request.
     */
    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientDisconnect(
            final Exception ex,
            final HttpServletRequest request
    ) {

        log.warn(
                "CLIENT DISCONNECTED at {} {} - {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
    }

    /**
     * Final fallback for unhandled exceptions.
     * @param ex the exception.
     * @param request the current request.
     * @return error response with INTERNAL_SERVER_ERROR status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(
            final Exception ex,
            final HttpServletRequest request
    ) {

        if (isIgnorableException(ex)) {

            log.warn(
                    "IGNORED NETWORK EXCEPTION at {} {} - {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.getClass().getSimpleName()
            );

            return null;
        }

        log.error(
                "UNHANDLED EXCEPTION at {} {} - {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex
        );

        ApiResponse<?> response = ApiResponse.error(
                "Internal server error",
                request.getRequestURI(),
                ErrorCode.INTERNAL_SERVER_ERROR
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * Handles 404 not found exceptions.
     * @param ex the exception.
     * @param request the current request.
     * @return error response with NOT_FOUND status.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(
            final NoHandlerFoundException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        "Endpoint not found",
                        request.getRequestURI(),
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
    }

    /**
     * Handles HTTP method not supported exceptions.
     * @param request the current request.
     * @return error response with METHOD_NOT_ALLOWED status.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(
                        "Method not allowed",
                        request.getRequestURI()
                ));
    }

    /**
     * Handle invalid budget threshold and scope exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return response with BAD_REQUEST status
     */
    @ExceptionHandler({
            InvalidBudgetThresholdException.class,
            InvalidBudgetScopeException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            final RuntimeException ex,
            final HttpServletRequest request
    ) {

        ErrorCode code = ex instanceof InvalidBudgetScopeException
                ? ErrorCode.INVALID_BUDGET_SCOPE
                : ErrorCode.INVALID_BUDGET_THRESHOLD;

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        code
                ));
    }

    /**
     * Handle budget overlap exceptions.
     *
     * @param ex the exception
     * @param request the HTTP request
     * @return response with CONFLICT status
     */
    @ExceptionHandler(BudgetOverlapException.class)
    public ResponseEntity<ApiResponse<Void>> handleBudgetOverlap(
            final BudgetOverlapException ex,
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.BUDGET_OVERLAP
                ));
    }

    /**
     * Handle errors related to email delivery and return a service
     * unavailable response.
     *
     * @param ex the email delivery exception
     * @param request the HTTP request
     * @return a service unavailable response entity
     */
    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailDeliveryException(
            final EmailDeliveryException ex,
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        ApiResponse.error(
                                ex.getMessage(),
                                request.getRequestURI(),
                                ErrorCode.EMAIL_DELIVERY_FAILED
                        )
                );
    }

    /**
     * Determines whether the exception is a harmless
     * network/client disconnect exception.
     *
     * @param ex the exception.
     * @return true if ignorable.
     */
    private boolean isIgnorableException(final Throwable ex) {

        Throwable current = ex;

        while (current != null) {

            if (current instanceof ClientAbortException
                    || current instanceof AsyncRequestNotUsableException) {
                return true;
            }

            String message = current.getMessage();

            if (message != null
                    && (
                    message.contains("Broken pipe")
                            || message.contains("Connection reset by peer")
            )) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    /**
     * Handles JPA entity not found exceptions.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with NOT_FOUND status
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(
            final EntityNotFoundException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
    }

    /**
     * Handles errors communicating with external services.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_GATEWAY status
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestClientException(
            final RestClientException ex,
            final HttpServletRequest request
    ) {
        log.error("Error comunicándose con Wompi", ex);
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(
                        "No fue posible comunicarse con el proveedor de pagos.",
                        request.getRequestURI()
                ));
    }

    /**
     * Handles malformed or unreadable HTTP request bodies.
     *
     * @param request the current HTTP request
     * @param ex the exception
     * @return an error response with BAD_REQUEST status
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadable(
            final HttpServletRequest request,
            final HttpMessageNotReadableException ex
    ) {

        String message = "El cuerpo de la solicitud es inválido.";

        Throwable cause = ex.getMostSpecificCause();

        if (cause instanceof InvalidFormatException invalidFormat) {

            String field = invalidFormat.getPath()
                    .stream()
                    .findFirst()
                    .map(ref -> ref.getPropertyName())
                    .orElse("desconocido");

            if (invalidFormat.getTargetType() == UUID.class) {
                message = "El campo '%s' debe ser un UUID válido."
                        .formatted(field);
            } else {
                message = "El campo '%s' tiene un formato inválido."
                        .formatted(field);
            }
        }

        return ApiResponse.error(
                message,
                request.getRequestURI(),
                ErrorCode.BAD_REQUEST
        );
    }

    /**
     * Handles subscription plan limit violations.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with PAYMENT_REQUIRED status
     */
    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlanLimitExceeded(
            final PlanLimitExceededException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponse.error(
                        ex.getMessage(), request.getRequestURI(),
                        ErrorCode.PLAN_LIMIT_EXCEEDED
                ));
    }

    /**
     * Handles database inconsistency errors caused by unexpected query results.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with CONFLICT status
     */
    @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleIncorrectResultSize(
            final IncorrectResultSizeDataAccessException ex,
            final HttpServletRequest request
    ) {

        log.error(
                "Database inconsistency at {}",
                request.getRequestURI(),
                ex
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "A data consistency problem was detected.",
                        request.getRequestURI()
                ));
    }

    /**
     * Handles business conflict exceptions.
     *
     * @param ex the exception.
     * @param request the current request.
     * @return error response with CONFLICT status.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            final ConflictException ex,
            final HttpServletRequest request
    ) {

        log.warn(
                "BUSINESS CONFLICT at {} {} - {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI(),
                        ErrorCode.CONFLICT
                ));
    }

    /**
     * Handles invalid request parameter values.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            final MethodArgumentTypeMismatchException ex,
            final HttpServletRequest request
    ) {

        String message = "Invalid value '%s' for parameter '%s'."
                .formatted(ex.getValue(), ex.getName());

        if (ex.getRequiredType() != null
                && ex.getRequiredType().isEnum()) {

            Object[] values = ex.getRequiredType().getEnumConstants();

            String allowedValues = java.util.Arrays.stream(values)
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "));

            message = "Invalid value '%s' for "
                    + "parameter '%s'. Allowed values: %s.".formatted(
                    ex.getValue(),
                    ex.getName(),
                    allowedValues
            );
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        message,
                        request.getRequestURI(),
                        ErrorCode.BAD_REQUEST
                ));
    }

    /**
     * Handles missing required request parameters.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
            final MissingServletRequestParameterException ex,
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "Missing required parameter '%s'."
                                .formatted(ex.getParameterName()),
                        request.getRequestURI(),
                        ErrorCode.BAD_REQUEST
                ));
    }

    /**
     * Handles missing required request headers.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(
            final MissingRequestHeaderException ex,
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "Missing required header '%s'."
                                .formatted(ex.getHeaderName()),
                        request.getRequestURI(),
                        ErrorCode.BAD_REQUEST
                ));
    }

    /**
     * Handles method parameter validation failures.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
            final HandlerMethodValidationException ex,
            final HttpServletRequest request
    ) {

        String message = ex.getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        message.isBlank()
                                ? "Request validation failed."
                                : message,
                        request.getRequestURI(),
                        ErrorCode.VALIDATION_ERROR
                ));
    }

    /**
     * Handles constraint validation failures.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            final ConstraintViolationException ex,
            final HttpServletRequest request
    ) {

        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        message,
                        request.getRequestURI(),
                        ErrorCode.VALIDATION_ERROR
                ));
    }

    /**
     * Handles unsupported media types.
     *
     * @param request the current HTTP request
     * @return a response with UNSUPPORTED_MEDIA_TYPE status
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(
                        "Unsupported media type.",
                        request.getRequestURI()
                ));
    }

    /**
     * Handles unacceptable response media types.
     *
     * @param request the current HTTP request
     * @return a response with NOT_ACCEPTABLE status
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .body(ApiResponse.error(
                        "Requested media type is not supported.",
                        request.getRequestURI()
                ));
    }

    /**
     * Handles database integrity constraint violations.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with CONFLICT status
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            final DataIntegrityViolationException ex,
            final HttpServletRequest request
    ) {

        log.warn(
                "DATABASE CONFLICT at {} {}",
                request.getMethod(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "The operation violates a database constraint.",
                        request.getRequestURI(),
                        ErrorCode.CONFLICT
                ));
    }

    /**
     * Handles optimistic locking failures.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with CONFLICT status
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
            final OptimisticLockingFailureException ex,
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "The resource was modified by another request.",
                        request.getRequestURI(),
                        ErrorCode.CONFLICT
                ));
    }

    /**
     * Handles binding errors.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            final BindException ex,
            final HttpServletRequest request
    ) {

        String errorMsg = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        errorMsg,
                        request.getRequestURI(),
                        ErrorCode.VALIDATION_ERROR
                ));
    }

    /**
     * Handle errors related to object storage (AWS S3) and return
     * a service unavailable response.
     *
     * @param ex the storage exception
     * @param request the HTTP request
     * @return a service unavailable response entity
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageException(
            final StorageException ex,
            final HttpServletRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        ApiResponse.error(
                                ex.getMessage(),
                                request.getRequestURI(),
                                ErrorCode.STORAGE_ERROR
                        )
                );
    }

    /**
     * Handles idempotency key reuse with a mismatched payload.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with CONFLICT status
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(
            final IdempotencyConflictException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        ex.getMessage(), request.getRequestURI(),
                        ErrorCode.IDEMPOTENCY_CONFLICT
                ));
    }

    /**
     * Handles invalid budget date ranges (e.g. end date before start).
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(InvalidBudgetDateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBudgetDate(
            final InvalidBudgetDateException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ex.getMessage(), request.getRequestURI(),
                        ErrorCode.INVALID_BUDGET_DATE
                ));
    }

    /**
     * Handles requests for a legal document version that doesn't exist.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with NOT_FOUND status
     */
    @ExceptionHandler(LegalDocumentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleLegalDocumentNotFound(
            final LegalDocumentNotFoundException ex,
            final HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        ex.getMessage(), request.getRequestURI(),
                        ErrorCode.LEGAL_DOCUMENT_NOT_FOUND
                ));
    }

    /**
     * Handles malformed multipart requests (e.g. missing boundary,
     * truncated body). Almost always a client-side issue — a manually
     * set Content-Type header without a boundary, or a non-multipart
     * body sent to a multipart endpoint — so it's logged as a warning
     * rather than an unhandled exception.
     *
     * @param ex the exception
     * @param request the current HTTP request
     * @return a response with BAD_REQUEST status
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(
            final MultipartException ex,
            final HttpServletRequest request
    ) {

        log.warn(
                "MALFORMED MULTIPART REQUEST at {} {} - {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "The multipart request is malformed or missing "
                                + "its boundary.",
                        request.getRequestURI(),
                        ErrorCode.BAD_REQUEST
                ));
    }

    /**
     * Extracts a stable, domain-level code from a field error — the
     * {@code {key}} used in the failing annotation's {@code message}
     * attribute (e.g. {@code "category.name.required"}), not Spring's
     * generic constraint-annotation name (e.g. {@code "NotBlank"}).
     * Falls back to Spring's own code when no underlying
     * {@link ConstraintViolation} is available (e.g. a JSON binding
     * failure rather than a business rule violation) or when the
     * annotation used a literal message instead of a {@code {key}}.
     *
     * @param error the field error.
     * @return a stable code suitable for frontend
     *         {@code t('validation.' + code)} lookups.
     */
    private String extractCode(final FieldError error) {

        if (error.contains(ConstraintViolation.class)) {

            ConstraintViolation<?> violation =
                    error.unwrap(ConstraintViolation.class);
            String template = violation.getMessageTemplate();

            if (template != null
                    && template.startsWith("{")
                    && template.endsWith("}")) {
                return template.substring(1, template.length() - 1);
            }
        }

        return error.getCode();
    }
}
