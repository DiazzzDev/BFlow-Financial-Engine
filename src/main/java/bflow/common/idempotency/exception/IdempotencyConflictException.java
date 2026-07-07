package bflow.common.idempotency.exception;

/**
 * Exception thrown when an idempotency key is reused with a different
 * request payload.
 */
public class IdempotencyConflictException extends RuntimeException {
    /**
     * Creates a new idempotency conflict exception.
     *
     * @param message exception message
     */
    public IdempotencyConflictException(final String message) {
        super(message);
    }
}
