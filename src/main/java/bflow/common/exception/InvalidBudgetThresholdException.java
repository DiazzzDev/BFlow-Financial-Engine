package bflow.common.exception;

/**
 * Exception thrown when budget thresholds are invalid.
 */
public class InvalidBudgetThresholdException
        extends IllegalStateException {

    public InvalidBudgetThresholdException(final String message) {
        super(message);
    }
}
