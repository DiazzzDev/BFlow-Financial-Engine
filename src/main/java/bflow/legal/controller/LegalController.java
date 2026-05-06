package bflow.legal.controller;

import bflow.legal.DTO.LegalDocumentResponse;
import bflow.legal.service.LegalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/legal")
@RequiredArgsConstructor
public final class LegalController {

    /** The legal service for retrieving legal documents. */
    private final LegalService legalService;

    /**
     * Retrieves a legal document of the specified type.
     * @param documentType the type of legal document to retrieve.
     * @param lang the language of the document (defaults to "en").
     * @return a response entity containing the legal document.
     */
    @GetMapping("/{documentType}")
    public ResponseEntity<LegalDocumentResponse> getDocument(
            @PathVariable final String documentType,
            @RequestParam(defaultValue = "en") final String lang
    ) {

        return ResponseEntity.ok(
                legalService.getDocument(documentType, lang)
        );
    }
}
