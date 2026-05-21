package bflow.common.exception;

/**
 * Exception thrown when budget thresholds are invalid.
 */
public class InvalidBudgetThresholdException
        extends IllegalStateException {

    /**
     * Construct an InvalidBudgetThresholdException with a message.
     *
     * @param message the exception message
     */
    public InvalidBudgetThresholdException(final String message) {
        super(message);
    }
}
