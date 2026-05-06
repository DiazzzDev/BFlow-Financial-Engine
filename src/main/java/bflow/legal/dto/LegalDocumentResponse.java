package bflow.legal.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LegalDocumentResponse {

    /** The type of legal document. */
    private String documentType;
    /** The language of the document. */
    private String language;
    /** The date when the document was last updated. */
    private String lastUpdated;
    /** The content of the legal document. */
    private String content;
    /** Contact email for document inquiries. */
    private String contactEmail;
}
