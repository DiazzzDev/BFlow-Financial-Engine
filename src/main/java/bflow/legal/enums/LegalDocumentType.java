package bflow.legal.enums;

/**
 * Enumeration of legal document types.
 */
public enum LegalDocumentType {

    /** Privacy policy document. */
    PRIVACY("privacy"),
    /** Terms of service document. */
    TERMS("terms"),
    /** Cookie policy document. */
    COOKIES("cookies");

    /** The string value of the document type. */
    private final String value;

    /**
     * Creates a new legal document type with the specified value.
     * @param valueParam the string value of the document type.
     */
    LegalDocumentType(final String valueParam) {
        this.value = valueParam;
    }

    /**
     * Retrieves the string value of this document type.
     * @return the string value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Retrieves a legal document type from its string value.
     * @param value the string value to look up.
     * @return the corresponding legal document type.
     */
    public static LegalDocumentType fromValue(final String value) {

        for (final LegalDocumentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
