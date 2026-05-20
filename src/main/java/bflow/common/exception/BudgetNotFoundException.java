package bflow.common.exception;

/**
 * Exception thrown when a budget is not found.
 */
public class BudgetNotFoundException extends NotFoundException {

    public BudgetNotFoundException(final String message) {
        super(message);
    }
}
