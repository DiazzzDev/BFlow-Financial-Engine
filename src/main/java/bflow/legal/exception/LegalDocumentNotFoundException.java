package bflow.legal.exception;

public class LegalDocumentNotFoundException extends RuntimeException {
    /**
     * Creates a new exception with the specified error message.
     * @param message the error message.
     */
    public LegalDocumentNotFoundException(final String message) {
        super(message);
    }
}

