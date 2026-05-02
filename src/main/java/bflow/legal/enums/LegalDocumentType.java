package bflow.legal.enums;

public enum LegalDocumentType {

    PRIVACY("privacy"),
    TERMS("terms"),
    COOKIES("cookies");

    private final String value;

    LegalDocumentType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LegalDocumentType fromValue(final String value) {

        for (LegalDocumentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                "Unsupported legal document type"
        );
    }
}