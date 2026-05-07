package bflow.legal.service;

import bflow.legal.dto.LegalDocumentResponse;

public interface LegalService {
    /**
     * Retrieves a legal document of the specified type in the given language.
     * @param documentType the type of legal document.
     * @param lang the language of the document.
     * @return the legal document response.
     */
    LegalDocumentResponse getDocument(
            String documentType,
            String lang
    );
}
