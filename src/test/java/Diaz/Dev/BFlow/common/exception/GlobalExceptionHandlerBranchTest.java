package Diaz.Dev.BFlow.common.exception;

import bflow.common.exception.EmailDeliveryException;
import bflow.common.exception.FileAccessDeniedException;
import bflow.common.exception.GlobalExceptionHandler;
import bflow.common.exception.InvalidBudgetScopeException;
import bflow.common.exception.InvalidBudgetThresholdException;
import bflow.common.exception.StorageException;
import bflow.common.response.ApiResponse;
import bflow.common.response.ErrorCode;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerBranchTest {

    private static final String REQUEST_PATH = "/api/v1/branch-test";

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn(REQUEST_PATH);
        when(request.getMethod()).thenReturn("POST");
    }

    @Test
    void illegalStateConflictPreservesMessageAndPath() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalState(
                new IllegalStateException("operation already completed"),
                request);

        assertError(response, HttpStatus.CONFLICT, "operation already completed",
                ErrorCode.CONFLICT);
    }

    @Test
    void accessDeniedWithNullMessageUsesSafeFallback() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(
                new AccessDeniedException(null), request);

        assertError(response, HttpStatus.FORBIDDEN, "Access denied",
                ErrorCode.FORBIDDEN);
    }

    @Test
    void invalidBudgetScopeUsesItsSpecificErrorCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(
                new InvalidBudgetScopeException("wallet scope required"),
                request);

        assertError(response, HttpStatus.BAD_REQUEST, "wallet scope required",
                ErrorCode.INVALID_BUDGET_SCOPE);
    }

    @Test
    void invalidBudgetThresholdUsesItsSpecificErrorCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(
                new InvalidBudgetThresholdException("threshold is invalid"),
                request);

        assertError(response, HttpStatus.BAD_REQUEST, "threshold is invalid",
                ErrorCode.INVALID_BUDGET_THRESHOLD);
    }

    @Test
    void fileAccessDeniedUsesFileSpecificCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleFileAccessDenied(
                        new FileAccessDeniedException("not the file owner"),
                        request);

        assertError(response, HttpStatus.FORBIDDEN, "not the file owner",
                ErrorCode.FILE_ACCESS_DENIED);
    }

    @Test
    void entityNotFoundIsMappedToResourceNotFound() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleEntityNotFound(
                        new EntityNotFoundException("row missing"), request);

        assertError(response, HttpStatus.NOT_FOUND, "row missing",
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void restClientFailureDoesNotExposeProviderDetails() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleRestClientException(
                        new RestClientException("provider token leaked"),
                        request);

        assertError(response, HttpStatus.BAD_GATEWAY,
                "No fue posible comunicarse con el proveedor de pagos.", null);
        assertFalse(response.getBody().getMessage().contains("provider token"));
    }

    @Test
    void databaseConstraintFailureDoesNotExposeConstraintDetails() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(
                        new DataIntegrityViolationException(
                                "users_email_unique_internal"), request);

        assertError(response, HttpStatus.CONFLICT,
                "The operation violates a database constraint.",
                ErrorCode.CONFLICT);
        assertFalse(response.getBody().getMessage().contains("users_email"));
    }

    @Test
    void malformedMultipartUsesStableClientFacingMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMultipartException(
                        new MultipartException("missing boundary detail"),
                        request);

        assertError(response, HttpStatus.BAD_REQUEST,
                "The multipart request is malformed or missing its boundary.",
                ErrorCode.BAD_REQUEST);
        assertFalse(response.getBody().getMessage().contains("missing boundary"));
    }

    @Test
    void storageFailureIsReturnedAsUnavailableWithDomainCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleStorageException(
                new StorageException("object storage unavailable"), request);

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE,
                "object storage unavailable", ErrorCode.STORAGE_ERROR);
    }

    @Test
    void emailDeliveryFailureIsReturnedAsUnavailable() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleEmailDeliveryException(
                        new EmailDeliveryException("mail temporarily unavailable"),
                        request);

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE,
                "mail temporarily unavailable", ErrorCode.EMAIL_DELIVERY_FAILED);
    }

    @Test
    void securityExceptionIsAuthenticationFailure() {
        ResponseEntity<ApiResponse<?>> response = handler.handleSecurityException(
                new SecurityException("invalid signature"), request);

        assertError(response, HttpStatus.UNAUTHORIZED, "invalid signature",
                ErrorCode.UNAUTHORIZED);
    }

    private void assertError(
            final ResponseEntity<? extends ApiResponse<?>> response,
            final HttpStatus status,
            final String message,
            final ErrorCode code
    ) {
        assertEquals(status, response.getStatusCode());
        assertEquals(REQUEST_PATH, response.getBody().getPath());
        assertEquals(message, response.getBody().getMessage());
        assertEquals(code, response.getBody().getCode());
        assertFalse(response.getBody().isSuccess());
    }
}
