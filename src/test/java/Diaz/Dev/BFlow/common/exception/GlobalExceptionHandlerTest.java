package Diaz.Dev.BFlow.common.exception;

import bflow.common.exception.BudgetOverlapException;
import bflow.common.exception.GlobalExceptionHandler;
import bflow.common.exception.InvalidBudgetDateException;
import bflow.common.financial.InvalidTransactionDateException;
import bflow.common.exception.PlanLimitExceededException;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.idempotency.exception.IdempotencyConflictException;
import bflow.common.response.ApiResponse;
import bflow.legal.exception.LegalDocumentNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler. Focused on two things:
 * exceptions that previously had NO handler and silently fell
 * through to the generic 500 handler (logging a full stack trace
 * per occurrence), and a sample of the existing mappings to guard
 * against regressions in status codes that client code depends on.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    // ---- Previously unhandled — these three exceptions fell through
    // to the generic 500 handler before this fix. ----

    @Test
    void handleIdempotencyConflict_returnsConflictNotServerError() {
        IdempotencyConflictException ex = new IdempotencyConflictException(
                "Idempotency key reused with a different payload");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIdempotencyConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Idempotency key reused with a different payload",
                response.getBody().getMessage());
    }

    @Test
    void handleInvalidBudgetDate_returnsBadRequestNotServerError() {
        InvalidBudgetDateException ex = new InvalidBudgetDateException(
                "End date must be after start date");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidBudgetDate(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void invalidTransactionDateReturnsBadRequestAndDateFieldError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidTransactionDate(
                        new InvalidTransactionDateException(
                                "Date must be between 1976-10-05 and 2026-10-05"),
                        request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("date", response.getBody().getErrors().getFirst().field());
        assertEquals("INVALID_TRANSACTION_DATE",
                response.getBody().getErrors().getFirst().code());
        assertEquals("Date must be between 1976-10-05 and 2026-10-05",
                response.getBody().getErrors().getFirst().message());
    }

    @Test
    void handleLegalDocumentNotFound_returnsNotFoundNotServerError() {
        LegalDocumentNotFoundException ex =
                new LegalDocumentNotFoundException(
                        "Terms of service v3 not found");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleLegalDocumentNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    // ---- Regression guards on existing mappings ----

    @Test
    void handlePlanLimitExceeded_returnsPaymentRequired() {
        PlanLimitExceededException ex = new PlanLimitExceededException(
                "Recurring transaction limit reached for FREE plan");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handlePlanLimitExceeded(ex, request);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
    }

    @Test
    void handleBudgetOverlap_returnsConflict() {
        BudgetOverlapException ex =
                new BudgetOverlapException("Budget period overlaps");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBudgetOverlap(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void handleWalletAccessDenied_returnsForbidden() {
        WalletAccessDeniedException ex =
                new WalletAccessDeniedException("Not a member of this wallet");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleWalletAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void handleOptimisticLockingFailure_returnsConflict() {
        OptimisticLockingFailureException ex =
                new OptimisticLockingFailureException("Row was modified");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleOptimisticLockingFailure(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        // The raw persistence-layer message should never leak to the
        // client — a stale row number or entity name is an internal
        // detail, not something the frontend should render.
        assertEquals("The resource was modified by another request.",
                response.getBody().getMessage());
    }

    @Test
    void handleResourceNotFound_returnsNotFound_viaNotFoundHierarchy() {
        // ResourceNotFoundException extends NotFoundException and has
        // no handler of its own — confirms the hierarchy-based dispatch
        // still resolves to the parent's @ExceptionHandler.
        ResourceNotFoundException ex =
                new ResourceNotFoundException("Expense not found");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ---- Generic fallback behavior ----

    @Test
    void handleGeneric_unknownException_returns500AndDoesNotLeakMessage() {
        RuntimeException ex = new RuntimeException(
                "Connection string: postgres://user:pw@host/db");

        ResponseEntity<ApiResponse<?>> response =
                handler.handleGeneric(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode());
        // Must never echo the raw exception message to the client —
        // could leak internal details (as in this deliberately
        // sensitive-looking example message).
        assertEquals("Internal server error",
                response.getBody().getMessage());
    }

    @Test
    void handleGeneric_clientAbortWrappedInCause_isIgnoredNotLogged500() {
        // isIgnorableException walks the cause chain, not just the
        // top-level type — confirms a wrapped ClientAbortException
        // (e.g. re-thrown by a stream-copy utility) is still detected.
        org.apache.catalina.connector.ClientAbortException abort =
                new org.apache.catalina.connector.ClientAbortException(
                        "Broken pipe");
        RuntimeException wrapped =
                new RuntimeException("Failed writing response", abort);

        ResponseEntity<ApiResponse<?>> response =
                handler.handleGeneric(wrapped, request);

        assertNull(response);
    }
}
