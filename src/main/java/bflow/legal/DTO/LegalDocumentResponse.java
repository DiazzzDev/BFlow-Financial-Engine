package bflow.legal.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LegalDocumentResponse {

    private String documentType;
    private String language;
    private String lastUpdated;
    private String content;
    private String contactEmail;
}