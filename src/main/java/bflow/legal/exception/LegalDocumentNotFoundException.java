package bflow.legal.exception;

public class LegalDocumentNotFoundException extends RuntimeException {
    public LegalDocumentNotFoundException(final String message) {
        super(message);
    }
}
