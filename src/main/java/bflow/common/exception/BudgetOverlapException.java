package bflow.common.exception;

/**
 * Exception thrown when a budget overlaps another budget.
 */
public class BudgetOverlapException
        extends RuntimeException {

    /**
     * Construct a BudgetOverlapException with a message.
     *
     * @param message the exception message
     */
    public BudgetOverlapException(final String message) {
        super(message);
    }
}
