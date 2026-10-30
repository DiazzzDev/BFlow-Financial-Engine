package Diaz.Dev.BFlow.common.exception;

import bflow.common.exception.EmailDeliveryException;
import bflow.common.exception.ConflictException;
import bflow.common.exception.FileAccessDeniedException;
import bflow.common.exception.GlobalExceptionHandler;
import bflow.common.exception.InvalidCredentialsException;
import bflow.common.exception.InvalidBudgetScopeException;
import bflow.common.exception.InvalidBudgetThresholdException;
import bflow.common.exception.NotFoundException;
import bflow.common.exception.StorageException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.response.ApiResponse;
import bflow.common.response.ErrorCode;
import bflow.dashboard.exception.InvalidStatisticsFilterException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void invalidCredentialsAreUnauthorized() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidCredentials(
                        new InvalidCredentialsException(), request);

        assertError(response, HttpStatus.UNAUTHORIZED, "Invalid credentials",
                ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void notFoundPreservesDomainMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(
                new NotFoundException("wallet not found"), request);

        assertError(response, HttpStatus.NOT_FOUND, "wallet not found",
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void illegalArgumentIsMappedToNotFound() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalArgument(
                        new IllegalArgumentException("category not found"),
                        request);

        assertError(response, HttpStatus.NOT_FOUND, "category not found",
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void walletAccessDeniedUsesWalletSpecificCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleWalletAccessDenied(
                        new WalletAccessDeniedException("not a wallet member"),
                        request);

        assertError(response, HttpStatus.FORBIDDEN, "not a wallet member",
                ErrorCode.WALLET_ACCESS_DENIED);
    }

    @Test
    void invalidStatisticsFilterIsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidStatisticsFilter(
                        new InvalidStatisticsFilterException(
                                "unsupported statistics period"),
                        request);

        assertError(response, HttpStatus.BAD_REQUEST,
                "unsupported statistics period", ErrorCode.BAD_REQUEST);
    }

    @Test
    void businessConflictPreservesMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleConflict(
                new ConflictException("invitation is no longer pending"),
                request);

        assertError(response, HttpStatus.CONFLICT,
                "invitation is no longer pending", ErrorCode.CONFLICT);
    }

    @Test
    void methodNotAllowedUsesStableResponse() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodNotAllowed(request);

        assertError(response, HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed", null);
    }

    @Test
    void unsupportedMediaTypeUsesStableResponse() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnsupportedMediaType(request);

        assertError(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type.", null);
    }

    @Test
    void unacceptableMediaTypeUsesStableResponse() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotAcceptable(request);

        assertError(response, HttpStatus.NOT_ACCEPTABLE,
                "Requested media type is not supported.", null);
    }

    @Test
    void genericFailureDoesNotExposeInternalExceptionDetails() {
        ResponseEntity<ApiResponse<?>> response = handler.handleGeneric(
                new IllegalStateException("database password detail"), request);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error", ErrorCode.INTERNAL_SERVER_ERROR);
        assertFalse(response.getBody().getMessage().contains("password"));
    }

    @Test
    void genericBrokenPipeIsIgnoredInsteadOfReportedAsServerFailure() {
        assertNull(handler.handleGeneric(
                new IllegalStateException("Broken pipe while writing"), request));
    }

    @Test
    void genericNestedConnectionResetIsIgnored() {
        RuntimeException cause =
                new RuntimeException("Connection reset by peer");

        assertNull(handler.handleGeneric(
                new IllegalStateException("response failed", cause), request));
    }

    @Test
    void unknownEndpointReturnsNotFound() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoHandlerFound(new NoHandlerFoundException(
                        "GET", "/unknown", new HttpHeaders()), request);

        assertError(response, HttpStatus.NOT_FOUND, "Endpoint not found",
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void validationErrorsExposeFieldAndConstraint() {
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError(
                "request", "title", null, false,
                new String[]{"NotBlank.request.title"}, null,
                "must not be blank"));
        MethodArgumentNotValidException exception =
                mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(binding);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleValidation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("title: must not be blank",
                response.getBody().getMessage());
        assertEquals("title", response.getBody().getErrors().getFirst().field());
        assertEquals("NotBlank.request.title",
                response.getBody().getErrors().getFirst().code());
    }

    @Test
    void invalidUuidBodyFieldNamesExpectedType() {
        InvalidFormatException cause = InvalidFormatException.from(
                null, "invalid UUID", "not-a-uuid", UUID.class);
        cause.prependPath(new Object(), "walletId");
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException(
                        "bad body", cause, new MockHttpInputMessage(new byte[0]));

        ApiResponse<Void> response =
                handler.handleHttpMessageNotReadable(request, exception);

        assertEquals(ErrorCode.BAD_REQUEST, response.getCode());
        assertEquals("El campo 'walletId' debe ser un UUID válido.",
                response.getMessage());
    }

    @Test
    void invalidEnumBodyFieldListsAcceptedValues() {
        InvalidFormatException cause = InvalidFormatException.from(
                null, "invalid period", "FORTNIGHTLY", SamplePeriod.class);
        cause.prependPath(new Object(), "period");
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException(
                        "bad body", cause, new MockHttpInputMessage(new byte[0]));

        ApiResponse<Void> response =
                handler.handleHttpMessageNotReadable(request, exception);

        assertEquals("El campo 'period' debe ser uno de: DAILY, MONTHLY.",
                response.getMessage());
    }

    @Test
    void malformedBodyUsesGenericClientMessage() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException(
                        "invalid json", new IllegalArgumentException("bad token"),
                        new MockHttpInputMessage(new byte[0]));

        ApiResponse<Void> response =
                handler.handleHttpMessageNotReadable(request, exception);

        assertEquals("El cuerpo de la solicitud es inválido.",
                response.getMessage());
        assertEquals(ErrorCode.BAD_REQUEST, response.getCode());
    }

    @Test
    void invalidEnumQueryParameterListsAcceptedValues() {
        MethodArgumentTypeMismatchException exception =
                mock(MethodArgumentTypeMismatchException.class);
        when(exception.getValue()).thenReturn("FORTNIGHTLY");
        when(exception.getName()).thenReturn("period");
        org.mockito.Mockito.doReturn(SamplePeriod.class)
                .when(exception).getRequiredType();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodArgumentTypeMismatch(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid value 'FORTNIGHTLY' for parameter 'period'. "
                        + "Allowed values: DAILY, MONTHLY.",
                response.getBody().getMessage());
    }

    @Test
    void missingRequiredParameterNamesTheParameter() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingRequestParameter(
                        new MissingServletRequestParameterException(
                                "walletId", "UUID"),
                        request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required parameter 'walletId'.",
                response.getBody().getMessage());
    }

    @Test
    void constraintViolationsReturnJoinedMessages() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> first = mock(ConstraintViolation.class);
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> second = mock(ConstraintViolation.class);
        when(first.getMessage()).thenReturn("must be positive");
        when(second.getMessage()).thenReturn("must not exceed limit");
        ConstraintViolationException exception =
                new ConstraintViolationException(Set.of(first, second));

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleConstraintViolation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, response.getBody().getCode());
        assertEquals(2, response.getBody().getMessage().split(", ").length);
    }

    @Test
    void bindingErrorsReturnFieldSpecificMessage() {
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError(
                "request", "amount", null, false,
                new String[]{"Positive.request.amount"}, null,
                "must be greater than zero"));
        org.springframework.validation.BindException exception =
                new org.springframework.validation.BindException(binding);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBindException(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, response.getBody().getCode());
        assertEquals("amount: must be greater than zero",
                response.getBody().getMessage());
    }

    @Test
    void duplicateDatabaseResultReturnsSafeConflictMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIncorrectResultSize(
                        new IncorrectResultSizeDataAccessException(1, 2),
                        request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("A data consistency problem was detected.",
                response.getBody().getMessage());
        assertEquals(REQUEST_PATH, response.getBody().getPath());
    }

    @Test
    void handlerMethodValidationJoinsProvidedMessages() {
        HandlerMethodValidationException exception =
                mock(HandlerMethodValidationException.class);
        org.mockito.Mockito.doReturn(List.of(
                new ObjectError("request", "must be valid"),
                new ObjectError("request", "must be authorized")))
                .when(exception).getAllErrors();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleHandlerMethodValidation(exception, request);

        assertEquals("must be valid, must be authorized",
                response.getBody().getMessage());
        assertEquals(ErrorCode.VALIDATION_ERROR, response.getBody().getCode());
    }

    private enum SamplePeriod {
        DAILY,
        MONTHLY
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
