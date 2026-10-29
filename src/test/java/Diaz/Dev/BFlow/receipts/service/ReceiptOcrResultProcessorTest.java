package Diaz.Dev.BFlow.receipts.service;

import bflow.receipts.entity.ReceiptUpload;
import bflow.receipts.enums.ReceiptStatus;
import bflow.receipts.service.ReceiptOcrResultProcessor;
import bflow.receipts.service.ReceiptStatusTransitionService;
import bflow.receipts.service.TextractExpenseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.ExpenseDetection;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.ExpenseType;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisResponse;
import software.amazon.awssdk.services.textract.model.TextractException;
import bflow.receipts.repository.RepositoryReceiptUpload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ReceiptOcrResultProcessorTest {

    @Mock private RepositoryReceiptUpload receiptRepository;
    @Mock private ReceiptStatusTransitionService transitionService;
    @Mock private TextractClient textractClient;
    @Mock private TextractExpenseMapper mapper;

    private ReceiptOcrResultProcessor processor;
    private final UUID receiptId = UUID.randomUUID();
    private final String jobId = "textract-job-123";
    private ReceiptUpload receipt;

    @BeforeEach
    void setUp() {
        processor = new ReceiptOcrResultProcessor(
                receiptRepository, transitionService, textractClient, mapper);
        receipt = new ReceiptUpload();
        receipt.setId(receiptId);
        receipt.setStatus(ReceiptStatus.PROCESSING);
        when(receiptRepository.findByTextractJobId(jobId))
                .thenReturn(Optional.of(receipt));
    }

    @Test
    void unknownJobIsIgnoredWithoutCallingAws() {
        when(receiptRepository.findByTextractJobId("unknown"))
                .thenReturn(Optional.empty());

        processor.processCompletion("unknown", "SUCCEEDED");

        verify(textractClient, never()).getExpenseAnalysis(any(GetExpenseAnalysisRequest.class));
        verify(transitionService, never()).markFailed(any(), any());
        verify(transitionService, never()).markExtracted(any(), any());
    }

    @Test
    void alreadyCompletedReceiptIgnoresRedeliveredNotification() {
        receipt.setStatus(ReceiptStatus.EXTRACTED);

        processor.processCompletion(jobId, "SUCCEEDED");

        verify(textractClient, never()).getExpenseAnalysis(any(GetExpenseAnalysisRequest.class));
        verify(transitionService, never()).markExtracted(any(), any());
    }

    @Test
    void failedTextractJobTransitionsReceiptToFailedWithoutPollingResult() {
        processor.processCompletion(jobId, "FAILED");

        verify(transitionService).markFailed(
                receiptId, "Textract could not process this document");
        verify(textractClient, never()).getExpenseAnalysis(any(GetExpenseAnalysisRequest.class));
    }

    @Test
    void succeededReceiptMapsAndPersistsOcrSuggestion() {
        GetExpenseAnalysisResponse response = receiptResponse("VENDOR_NAME");
        var extraction = new ReceiptStatusTransitionService.ReceiptOcrExtraction(
                bflow.receipts.enums.ReceiptTransactionType.EXPENSE,
                "Market", null, null, null, "{}");
        when(textractClient.getExpenseAnalysis(any(GetExpenseAnalysisRequest.class)))
                .thenReturn(response);
        when(mapper.map(response)).thenReturn(extraction);

        processor.processCompletion(jobId, "SUCCEEDED");

        verify(mapper).map(response);
        verify(transitionService).markExtracted(receiptId, extraction);
    }

    @Test
    void partialSuccessStillProcessesReceiptFields() {
        GetExpenseAnalysisResponse response = receiptResponse("TOTAL");
        var extraction = new ReceiptStatusTransitionService.ReceiptOcrExtraction(
                bflow.receipts.enums.ReceiptTransactionType.EXPENSE,
                null, null, null, null, "{}");
        when(textractClient.getExpenseAnalysis(any(GetExpenseAnalysisRequest.class)))
                .thenReturn(response);
        when(mapper.map(response)).thenReturn(extraction);

        processor.processCompletion(jobId, "PARTIAL_SUCCESS");

        verify(transitionService).markExtracted(receiptId, extraction);
    }

    @Test
    void emptyTextractResponseUsesFailedFallbackForManualReupload() {
        when(textractClient.getExpenseAnalysis(any(GetExpenseAnalysisRequest.class)))
                .thenReturn(GetExpenseAnalysisResponse.builder()
                        .expenseDocuments(List.of()).build());

        processor.processCompletion(jobId, "SUCCEEDED");

        verify(transitionService).markFailed(receiptId, "No receipt fields detected");
        verify(mapper, never()).map(any());
    }

    @Test
    void arbitraryDocumentWithoutReceiptSignalsIsRejected() {
        GetExpenseAnalysisResponse response = receiptResponse("ITEM_NAME");
        when(textractClient.getExpenseAnalysis(any(GetExpenseAnalysisRequest.class)))
                .thenReturn(response);

        processor.processCompletion(jobId, "SUCCEEDED");

        verify(transitionService).markFailed(receiptId, "No receipt fields detected");
        verify(mapper, never()).map(any());
    }

    @Test
    void transientTextractFailurePropagatesForQueueRedelivery() {
        AwsServiceException outage = TextractException.builder()
                .message("temporarily unavailable").build();
        when(textractClient.getExpenseAnalysis(any(GetExpenseAnalysisRequest.class)))
                .thenThrow(outage);

        assertThrows(TextractException.class,
                () -> processor.processCompletion(jobId, "SUCCEEDED"));

        verify(transitionService, never()).markFailed(any(), any());
        verify(transitionService, never()).markExtracted(any(), any());
    }

    private GetExpenseAnalysisResponse receiptResponse(String fieldType) {
        ExpenseField field = ExpenseField.builder()
                .type(ExpenseType.builder().text(fieldType).build())
                .valueDetection(ExpenseDetection.builder()
                        .text("Market").confidence(90f).build())
                .build();
        return GetExpenseAnalysisResponse.builder()
                .expenseDocuments(ExpenseDocument.builder()
                        .summaryFields(field).build())
                .build();
    }
}
