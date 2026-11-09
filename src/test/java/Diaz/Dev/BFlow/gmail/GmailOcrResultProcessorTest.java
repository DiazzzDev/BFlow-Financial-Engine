package Diaz.Dev.BFlow.gmail;

import bflow.common.aws.service.StorageService;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.service.GmailExpenseRegistrar;
import bflow.gmail.service.GmailImportStateService;
import bflow.gmail.service.GmailOcrResultProcessor;
import bflow.receipts.enums.ReceiptTransactionType;
import bflow.receipts.service.ReceiptStatusTransitionService.ReceiptOcrExtraction;
import bflow.receipts.service.TextractExpenseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GmailOcrResultProcessorTest {
    private GmailImportRepository imports;
    private GmailImportStateService state;
    private StorageService storage;
    private TextractClient textract;
    private TextractExpenseMapper mapper;
    private GmailExpenseRegistrar registrar;
    private GmailOcrResultProcessor processor;
    private GmailImport item;
    private UUID importId;

    @BeforeEach
    void setUp() {
        imports = mock(GmailImportRepository.class);
        state = mock(GmailImportStateService.class);
        storage = mock(StorageService.class);
        textract = mock(TextractClient.class);
        mapper = mock(TextractExpenseMapper.class);
        registrar = mock(GmailExpenseRegistrar.class);
        processor = new GmailOcrResultProcessor(imports, state, storage, textract, mapper, registrar);
        importId = UUID.randomUUID();
        item = new GmailImport();
        item.setId(importId);
        item.setStatus("PROCESSING");
        item.setS3ObjectKey("tmp/gmail/staged-object");
    }

    @Test
    void ignoresUnknownJobsAndAlreadyCompletedImports() {
        when(imports.findByTextractJobId("unknown")).thenReturn(Optional.empty());
        assertThat(processor.processCompletion("unknown", "SUCCEEDED")).isFalse();

        when(imports.findByTextractJobId("done-job")).thenReturn(Optional.of(item));
        item.setStatus("COMPLETED");
        assertThat(processor.processCompletion("done-job", "SUCCEEDED")).isTrue();
        verify(textract, never()).getExpenseAnalysis(any(software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest.class));
        verify(storage, never()).delete(any());
    }

    @Test
    void marksTextractFailureAndDeletesStagedFile() {
        when(imports.findByTextractJobId("failed-job")).thenReturn(Optional.of(item));

        assertThat(processor.processCompletion("failed-job", "FAILED")).isTrue();

        verify(state).markFailed(importId, "TEXTRACT_FAILED");
        verify(storage).delete("tmp/gmail/staged-object");
        verify(textract, never()).getExpenseAnalysis(any(software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest.class));
    }

    @Test
    void marksMissingOrNonPositiveAmountsAsFailedAndDeletesStagedFile() {
        when(imports.findByTextractJobId("no-amount")).thenReturn(Optional.of(item));
        when(textract.getExpenseAnalysis(any(software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest.class)))
                .thenReturn(GetExpenseAnalysisResponse.builder().build());
        when(mapper.map(any(GetExpenseAnalysisResponse.class))).thenReturn(
                extraction(null, BigDecimal.ZERO, "Receipt"));

        assertThat(processor.processCompletion("no-amount", "SUCCEEDED")).isTrue();

        verify(state).markFailed(importId, "OCR_NO_AMOUNT");
        verify(storage).delete("tmp/gmail/staged-object");
        verify(registrar, never()).register(any(), any(), any(), any(), any());
    }

    @Test
    void registersSanitizedTransactionAndDeletesStagedFile() {
        when(imports.findByTextractJobId("success-job")).thenReturn(Optional.of(item));
        when(textract.getExpenseAnalysis(any(software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest.class)))
                .thenReturn(GetExpenseAnalysisResponse.builder().build());
        when(mapper.map(any(GetExpenseAnalysisResponse.class))).thenReturn(
                extraction(ReceiptTransactionType.INCOME, new BigDecimal("18.25"), "Pago <script>alert(1)</script> #1"));

        assertThat(processor.processCompletion("success-job", "SUCCEEDED")).isTrue();

        verify(registrar).register(importId, "Pago script alert(1) /script #1", new BigDecimal("18.25"),
                LocalDate.of(2026, 11, 9), ReceiptTransactionType.INCOME);
        verify(storage).delete("tmp/gmail/staged-object");
    }

    @Test
    void deletesFileEvenWhenStorageReportsDeletionFailure() {
        when(imports.findByTextractJobId("failed-delete")).thenReturn(Optional.of(item));
        when(textract.getExpenseAnalysis(any(software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest.class)))
                .thenReturn(GetExpenseAnalysisResponse.builder().build());
        when(mapper.map(any(GetExpenseAnalysisResponse.class))).thenReturn(
                extraction(ReceiptTransactionType.EXPENSE, new BigDecimal("2.00"), "Meal"));
        org.mockito.Mockito.doThrow(new IllegalStateException("S3 unavailable"))
                .when(storage).delete("tmp/gmail/staged-object");

        assertThat(processor.processCompletion("failed-delete", "SUCCEEDED")).isTrue();
        verify(registrar).register(any(), any(), any(), any(), any());
        verify(storage).delete("tmp/gmail/staged-object");
    }

    private ReceiptOcrExtraction extraction(ReceiptTransactionType type, BigDecimal amount, String title) {
        return new ReceiptOcrExtraction(type, title, amount, LocalDate.of(2026, 11, 9), null, null);
    }
}
