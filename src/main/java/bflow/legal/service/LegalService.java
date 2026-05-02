package bflow.legal.service;

import bflow.legal.DTO.LegalDocumentResponse;

public interface LegalService {
    LegalDocumentResponse getDocument(
            String documentType,
            String lang
    );
}
