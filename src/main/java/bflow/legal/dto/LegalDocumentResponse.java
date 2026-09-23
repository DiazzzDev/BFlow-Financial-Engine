package bflow.legal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LegalDocumentResponse {

    /** The type of legal document. */
    @Schema(description = "Legal document type, serialized in lowercase.",
            allowableValues = {"privacy", "terms", "cookies"})
    private String documentType;
    /** The language of the document. */
    @Schema(description = "Language of the returned document.",
            allowableValues = {"en", "es"})
    private String language;
    /** The date when the document was last updated. */
    private String lastUpdated;
    /** The content of the legal document. */
    private String content;
    /** Contact email for document inquiries. */
    private String contactEmail;
}
