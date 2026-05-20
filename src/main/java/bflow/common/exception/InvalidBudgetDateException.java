package bflow.common.exception;

public class InvalidBudgetDateException
        extends RuntimeException {

    public InvalidBudgetDateException(
            final String message
    ) {
        super(message);
    }
}