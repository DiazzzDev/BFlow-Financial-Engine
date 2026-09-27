package bflow.dashboard.exception;

/**
 * Raised when dashboard statistics query parameters do not form a valid
 * period or date range.
 */
public class InvalidStatisticsFilterException extends RuntimeException {

    /**
     * Creates the exception with its user-facing validation message.
     *
     * @param message invalid-filter explanation
     */
    public InvalidStatisticsFilterException(final String message) {
        super(message);
    }
}
