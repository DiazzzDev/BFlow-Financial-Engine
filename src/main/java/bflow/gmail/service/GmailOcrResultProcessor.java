package bflow.gmail.service;

import bflow.common.aws.service.StorageService;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.repository.GmailImportRepository;
import bflow.receipts.service.ReceiptStatusTransitionService.ReceiptOcrExtraction;
import bflow.receipts.service.TextractExpenseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest;

/** Completes Gmail's S3-backed Textract jobs and always removes the staging object. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmailOcrResultProcessor {
    private final GmailImportRepository importRepository;
    private final GmailImportStateService stateService;
    private final StorageService storageService;
    private final TextractClient textractClient;
    private final TextractExpenseMapper textractMapper;
    private final GmailExpenseRegistrar registrar;

    public boolean processCompletion(final String jobId, final String status) {
        GmailImport item = importRepository.findByTextractJobId(jobId).orElse(null);
        if (item == null) return false;
        if (!"PROCESSING".equals(item.getStatus())) return true;
        boolean terminal = false;
        try {
            if ("FAILED".equals(status)) {
                stateService.markFailed(item.getId(), "TEXTRACT_FAILED");
                terminal = true;
                return true;
            }
            ReceiptOcrExtraction extraction = textractMapper.map(textractClient.getExpenseAnalysis(
                    GetExpenseAnalysisRequest.builder().jobId(jobId).build()));
            if (extraction.suggestedAmount() == null || extraction.suggestedAmount().signum() <= 0) {
                stateService.markFailed(item.getId(), "OCR_NO_AMOUNT");
                terminal = true;
                return true;
            }
            registrar.register(item.getId(), safeTitle(extraction.suggestedTitle()), extraction.suggestedAmount(),
                    extraction.suggestedDate(), extraction.suggestedType());
            terminal = true;
            return true;
        } finally {
            if (terminal && item.getS3ObjectKey() != null) {
                try { storageService.delete(item.getS3ObjectKey()); }
                catch (RuntimeException ex) { log.warn("Could not remove Gmail staging object for import {}", item.getId()); }
            }
        }
    }

    private String safeTitle(final String value) {
        if (value == null || value.isBlank()) return "Movimiento desde Gmail";
        String safe = value.replaceAll("[^\\p{L}0-9 .,'\\-()!?&@#%/+:_]", " ")
                .replaceAll("\\s+", " ").trim();
        return safe.isBlank() ? "Movimiento desde Gmail" : safe.substring(0, Math.min(50, safe.length()));
    }
}
