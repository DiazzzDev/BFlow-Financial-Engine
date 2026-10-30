package bflow.common.financial;

/** Raised when a transaction date is missing or outside business policy. */
public class InvalidTransactionDateException extends IllegalArgumentException {

    /**
     * Creates a date validation failure.
     *
     * @param message localized explanation
     */
    public InvalidTransactionDateException(final String message) {
        super(message);
    }
}
