package bflow.common.exception;

import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * Global controller advice to handle application-wide exceptions.
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
            .body(ApiResponse.error(ex.getMessage(), request.getRequestURI()));
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
                        request.getRequestURI()
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
                        request.getRequestURI()
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
                        request.getRequestURI()
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
                        request.getRequestURI()
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
                        request.getRequestURI()
                ));
    }

    /**
     * Handles bean validation errors.
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
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(errorMsg, request.getRequestURI()));
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
            final HttpServletRequest request) {
        log.error("UNHANDLED EXCEPTION at {} {} - {}",
                request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName(), ex);

        ApiResponse<?> response = ApiResponse.error(
                "Internal server error",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
                        request.getRequestURI()
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

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        request.getRequestURI()
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
                        request.getRequestURI()
                ));
    }
}
