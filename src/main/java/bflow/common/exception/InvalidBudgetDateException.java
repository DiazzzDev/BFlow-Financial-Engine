package bflow.common.exception;

/**
 * Exception thrown when a budget date is invalid.
 */
public class InvalidBudgetDateException
        extends RuntimeException {

    /**
     * Construct an InvalidBudgetDateException with a message.
     *
     * @param message the exception message
     */
    public InvalidBudgetDateException(
            final String message
    ) {
        super(message);
    }
}

