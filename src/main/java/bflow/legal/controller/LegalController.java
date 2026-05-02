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
public class LegalController {

    private final LegalService legalService;

    @GetMapping("/{documentType}")
    public ResponseEntity<LegalDocumentResponse> getDocument(
            @PathVariable String documentType,
            @RequestParam(defaultValue = "en") final String lang
    ) {

        return ResponseEntity.ok(
                legalService.getDocument(documentType, lang)
        );
    }
}
