package bflow.common.exception;

/**
 * Exception thrown when budget scope configuration is invalid.
 */
public class InvalidBudgetScopeException
        extends IllegalStateException {

    /**
     * Construct an InvalidBudgetScopeException with a message.
     *
     * @param message the exception message
     */
    public InvalidBudgetScopeException(final String message) {
        super(message);
    }
}
