package bflow.common.exception;

/**
 * Exception thrown when budget scope configuration is invalid.
 */
public class InvalidBudgetScopeException
        extends IllegalStateException {

    public InvalidBudgetScopeException(final String message) {
        super(message);
    }
}
