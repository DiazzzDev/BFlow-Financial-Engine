package bflow.common.exception;

/**
 * Exception thrown when a budget is not found.
 */
public class BudgetNotFoundException extends NotFoundException {

    /**
     * Construct a BudgetNotFoundException with a message.
     *
     * @param message the exception message
     */
    public BudgetNotFoundException(final String message) {
        super(message);
    }
}
