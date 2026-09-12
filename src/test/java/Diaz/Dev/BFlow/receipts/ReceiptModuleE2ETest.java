package Diaz.Dev.BFlow.receipts;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.CurrentUserService;
import bflow.common.aws.service.StorageService;
import bflow.common.exception.FileAccessDeniedException;
import bflow.common.i18n.MessageService;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.services.ServiceExpense;
import bflow.income.DTO.IncomeRequest;
import bflow.income.DTO.IncomeResponse;
import bflow.income.ServiceIncome;
import bflow.receipts.DTO.ReceiptConfirmRequest;
import bflow.receipts.DTO.ReceiptUploadRequest;
import bflow.receipts.controllers.ControllerReceiptUpload;
import bflow.receipts.entity.ReceiptUpload;
import bflow.receipts.enums.ReceiptStatus;
import bflow.receipts.enums.ReceiptTransactionType;
import bflow.receipts.event.ReceiptRegisteredEvent;
import bflow.receipts.messaging.ReceiptOcrRequestEventListener;
import bflow.receipts.messaging.ReceiptOcrRequestListener;
import bflow.receipts.messaging.ReceiptOcrResultListener;
import bflow.receipts.repository.RepositoryReceiptUpload;
import bflow.receipts.service.ReceiptOcrJobStarter;
import bflow.receipts.service.ReceiptOcrRequestPublisher;
import bflow.receipts.service.ReceiptOcrResultProcessor;
import bflow.receipts.service.ReceiptStatusTransitionService;
import bflow.receipts.service.ReceiptUploadService;
import bflow.receipts.service.TextractExpenseMapper;
import bflow.storage.entity.StoredFile;
import bflow.storage.enums.FileStatus;
import bflow.storage.repository.RepositoryStoredFile;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.ExpenseDetection;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.ExpenseType;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisResponse;
import software.amazon.awssdk.services.textract.model.StartExpenseAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartExpenseAnalysisResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests for the receipts module: they wire together the
 * real {@link ControllerReceiptUpload}, {@link ReceiptUploadService},
 * messaging layer ({@link ReceiptOcrRequestEventListener}, {@link
 * ReceiptOcrRequestListener}, {@link ReceiptOcrResultListener}) and
 * OCR services ({@link ReceiptOcrJobStarter}, {@link
 * ReceiptOcrResultProcessor}, {@link TextractExpenseMapper}, {@link
 * ReceiptStatusTransitionService}) exactly as Spring would, and only
 * mock the true boundaries of the module: {@link TextractClient},
 * {@link SqsClient}, and the repositories/services belonging to
 * other modules (storage, wallet, auth, expenses, income).
 *
 * <p>There is no Spring context, Testcontainers, or embedded SQS in
 * this project yet, so instead of starting the real polling threads
 * this test invokes each listener's package-private {@code handle}
 * method directly to simulate a message arriving off the queue —
 * deterministic, and exercises the exact same code a real poll loop
 * would run. {@link RepositoryReceiptUpload} is backed by a small
 * in-memory map so that state genuinely flows from one pipeline
 * stage to the next, the way it would through a real database.</p>
 *
 * <p>Strictness is relaxed to LENIENT at the class level: most
 * fixtures are shared via {@code @BeforeEach}, but not every
 * scenario exercises every stub.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiptModuleE2ETest {

    private static final String BUCKET = "bflow-files-test";
    private static final String REQUEST_QUEUE_URL =
            "https://sqs.test/receipt-ocr-requests";
    private static final String RESULTS_QUEUE_URL =
            "https://sqs.test/receipt-ocr-results";
    private static final String RESULTS_TOPIC_ARN =
            "arn:aws:sns:test:receipt-ocr-results";
    private static final String TEXTRACT_SNS_ROLE_ARN =
            "arn:aws:iam::test:role/textract-sns";

    @Mock private RepositoryReceiptUpload repositoryReceiptUpload;
    @Mock private RepositoryStoredFile repositoryStoredFile;
    @Mock private RepositoryWalletUser repositoryWalletUser;
    @Mock private RepositoryUser repositoryUser;
    @Mock private ServiceExpense serviceExpense;
    @Mock private ServiceIncome serviceIncome;
    @Mock private StorageService storageService;
    @Mock private SqsClient sqsClient;
    @Mock private TextractClient textractClient;
    @Mock private CurrentUserService currentUserService;
    @Mock private Authentication authentication;
    @Mock private HttpServletRequest httpServletRequest;

    @Mock private MessageService messageService;

    /** In-memory "database" backing repositoryReceiptUpload. */
    private final Map<UUID, ReceiptUpload> store = new HashMap<>();

    private ControllerReceiptUpload controller;
    private ReceiptOcrRequestListener requestListener;
    private ReceiptOcrResultListener resultListener;
    private ReceiptOcrJobStarter jobStarter;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private User user;
    private StoredFile storedFile;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wireInMemoryRepository();

        user = new User();
        user.setId(userId);

        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Main wallet");
        wallet.setCurrency(Currency.USD);

        storedFile = new StoredFile();
        storedFile.setId(fileId);
        storedFile.setUser(user);
        storedFile.setObjectKey("users/" + userId + "/receipt.jpg");
        storedFile.setOriginalFilename("receipt.jpg");
        storedFile.setContentType("image/jpeg");
        storedFile.setSizeBytes(1024L);
        storedFile.setStatus(FileStatus.UPLOADED);

        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(wallet);
        walletUser.setUser(user);
        walletUser.setRole(WalletRole.OWNER);

        when(repositoryStoredFile.findByIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(storedFile));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryUser.getReferenceById(userId)).thenReturn(user);
        when(currentUserService.getCurrentUserId(authentication))
                .thenReturn(userId);

        // Echo the status/argument back into the stubbed message so
        // assertions on "FAILED"/"DISCARDED"/etc. below still see it,
        // regardless of what the real Spanish/English wording is.
        when(messageService.get(
                eq("receipt.cannotConfirm.wrongStatus"), any()
        )).thenAnswer(inv ->
                "Receipt cannot be confirmed from status "
                        + inv.getArgument(1));
        when(messageService.get(eq("receipt.confirmed.cannotDiscard")))
                .thenReturn("A confirmed receipt cannot be discarded");

        // Real event publisher stand-in for Spring's AFTER_COMMIT
        // proxy: since there is no real transaction in this test,
        // publishing is wired to synchronously invoke the listener,
        // exactly what happens once ReceiptUploadService#register's
        // transaction commits in production.
        ReceiptOcrRequestPublisher ocrRequestPublisher =
                new ReceiptOcrRequestPublisher(sqsClient);
        ReflectionTestUtils.setField(
                ocrRequestPublisher, "requestsQueueUrl", REQUEST_QUEUE_URL);
        ReceiptOcrRequestEventListener requestEventListener =
                new ReceiptOcrRequestEventListener(ocrRequestPublisher);
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof ReceiptRegisteredEvent registered) {
                requestEventListener.onReceiptRegistered(registered);
            }
        };

        ReceiptUploadService receiptUploadService = new ReceiptUploadService(
                repositoryReceiptUpload, repositoryStoredFile,
                repositoryWalletUser, repositoryUser, messageService,
                serviceExpense, serviceIncome, storageService, publisher);

        controller = new ControllerReceiptUpload(
                receiptUploadService, currentUserService, messageService);

        ReceiptStatusTransitionService statusTransitionService =
                new ReceiptStatusTransitionService(repositoryReceiptUpload);

        jobStarter = new ReceiptOcrJobStarter(
                repositoryReceiptUpload, statusTransitionService,
                textractClient);
        ReflectionTestUtils.setField(jobStarter, "bucket", BUCKET);
        ReflectionTestUtils.setField(
                jobStarter, "resultsTopicArn", RESULTS_TOPIC_ARN);
        ReflectionTestUtils.setField(
                jobStarter, "textractSnsRoleArn", TEXTRACT_SNS_ROLE_ARN);

        requestListener = new ReceiptOcrRequestListener(
                sqsClient, jobStarter, REQUEST_QUEUE_URL, 1, 5, 1);

        TextractExpenseMapper mapper =
                new TextractExpenseMapper(new ObjectMapper());
        ReceiptOcrResultProcessor resultProcessor = new ReceiptOcrResultProcessor(
                repositoryReceiptUpload, statusTransitionService,
                textractClient, mapper);

        resultListener = new ReceiptOcrResultListener(
                sqsClient, resultProcessor, new ObjectMapper(),
                RESULTS_QUEUE_URL, 1, 5, 1);
    }

    private void wireInMemoryRepository() {
        when(repositoryReceiptUpload.save(any(ReceiptUpload.class)))
                .thenAnswer(inv -> {
                    ReceiptUpload receipt = inv.getArgument(0);
                    if (receipt.getId() == null) {
                        receipt.setId(UUID.randomUUID());
                    }
                    store.put(receipt.getId(), receipt);
                    return receipt;
                });
        when(repositoryReceiptUpload.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(
                        store.get(inv.getArgument(0, UUID.class))));
        when(repositoryReceiptUpload.findByIdWithStoredFile(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(
                        store.get(inv.getArgument(0, UUID.class))));
        when(repositoryReceiptUpload.findByIdAndUserId(
                any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    ReceiptUpload receipt =
                            store.get(inv.getArgument(0, UUID.class));
                    UUID requestedUserId = inv.getArgument(1, UUID.class);
                    if (receipt == null
                            || !receipt.getUser().getId().equals(requestedUserId)) {
                        return Optional.empty();
                    }
                    return Optional.of(receipt);
                });
        when(repositoryReceiptUpload.findByTextractJobId(anyString()))
                .thenAnswer(inv -> store.values().stream()
                        .filter(r -> inv.getArgument(0, String.class)
                                .equals(r.getTextractJobId()))
                        .findFirst());
        when(repositoryReceiptUpload.existsByStoredFileId(any(UUID.class)))
                .thenAnswer(inv -> store.values().stream()
                        .anyMatch(r -> r.getStoredFile().getId()
                                .equals(inv.getArgument(0, UUID.class))));
    }

    // ---- fixtures -----------------------------------------------------

    private Message requestMessage(final UUID receiptId) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .receiptHandle("handle-" + receiptId)
                .body(receiptId.toString())
                .build();
    }

    private Message resultMessage(final String body) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .receiptHandle("handle-result")
                .body(body)
                .build();
    }

    private String snsCompletionBody(
            final String jobId, final String status) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String inner = mapper.writeValueAsString(Map.of(
                "JobId", jobId, "Status", status,
                "API", "StartExpenseAnalysis"));
        return mapper.writeValueAsString(Map.of(
                "Type", "Notification", "Message", inner));
    }

    private ExpenseField summaryField(
            final String type, final String value, final float confidence) {
        return ExpenseField.builder()
                .type(ExpenseType.builder()
                        .text(type)
                        .confidence(confidence)
                        .build())
                .valueDetection(ExpenseDetection.builder()
                        .text(value)
                        .confidence(confidence)
                        .build())
                .build();
    }

    private GetExpenseAnalysisResponse textractSuccessResponse() {
        ExpenseDocument document = ExpenseDocument.builder()
                .summaryFields(List.of(
                        summaryField("VENDOR_NAME", "Super Selectos", 98.5f),
                        summaryField("TOTAL", "$45.30", 95.2f),
                        summaryField(
                                "INVOICE_RECEIPT_DATE", "08/20/2026", 91.0f)))
                .build();
        return GetExpenseAnalysisResponse.builder()
                .expenseDocuments(List.of(document))
                .build();
    }

    // ---- happy path -----------------------------------------------------

    @Test
    void happyPathFullPipelineRegisterThroughConfirmAsExpense() throws Exception {
        UUID receiptId = registerReceipt();
        assertThat(store.get(receiptId).getStatus())
                .isEqualTo(ReceiptStatus.RECEIVED);

        // OCR request enqueued exactly once, carrying the receipt id.
        ArgumentCaptor<SendMessageRequest> sendCaptor =
                ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sendCaptor.capture());
        assertThat(sendCaptor.getValue().queueUrl()).isEqualTo(REQUEST_QUEUE_URL);
        assertThat(sendCaptor.getValue().messageBody())
                .isEqualTo(receiptId.toString());

        // Request listener picks up the message and starts the job.
        when(textractClient.startExpenseAnalysis(
                any(StartExpenseAnalysisRequest.class)))
                .thenReturn(StartExpenseAnalysisResponse.builder()
                        .jobId("job-123")
                        .build());
        boolean requestHandled = invokeHandle(
                requestListener, requestMessage(receiptId));
        assertThat(requestHandled).isTrue();
        assertThat(store.get(receiptId).getStatus())
                .isEqualTo(ReceiptStatus.PROCESSING);
        assertThat(store.get(receiptId).getTextractJobId())
                .isEqualTo("job-123");

        // Result listener picks up the SNS completion notification.
        when(textractClient.getExpenseAnalysis(
                any(GetExpenseAnalysisRequest.class)))
                .thenReturn(textractSuccessResponse());
        boolean resultHandled = invokeHandle(resultListener,
                resultMessage(snsCompletionBody("job-123", "SUCCEEDED")));
        assertThat(resultHandled).isTrue();

        ReceiptUpload extracted = store.get(receiptId);
        assertThat(extracted.getStatus()).isEqualTo(ReceiptStatus.EXTRACTED);
        assertThat(extracted.getSuggestedTitle()).isEqualTo("Super Selectos");
        assertThat(extracted.getSuggestedAmount())
                .isEqualByComparingTo(new BigDecimal("45.30"));
        assertThat(extracted.getSuggestedDate())
                .isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(extracted.getConfidenceScore()).isNotNull();
        assertThat(extracted.getRawOcrPayload()).contains("VENDOR_NAME");

        // Poll: the frontend sees EXTRACTED before confirming.
        ResponseEntity<?> statusResponse = controller.getStatus(
                receiptId, authentication, httpServletRequest);
        assertThat(statusResponse.getStatusCode().value()).isEqualTo(200);

        // Confirm into an Expense.
        ExpenseResponse expenseResponse = new ExpenseResponse();
        UUID expenseId = UUID.randomUUID();
        expenseResponse.setId(expenseId.toString());
        ArgumentCaptor<ExpenseRequest> expenseCaptor =
                ArgumentCaptor.forClass(ExpenseRequest.class);
        when(serviceExpense.newExpense(expenseCaptor.capture(), eq(userId)))
                .thenReturn(expenseResponse);

        ReceiptConfirmRequest confirmRequest = new ReceiptConfirmRequest();
        confirmRequest.setType(ReceiptTransactionType.EXPENSE);
        confirmRequest.setTitle("Super Selectos");
        confirmRequest.setAmount(new BigDecimal("45.30"));
        confirmRequest.setCategoryId(categoryId);
        confirmRequest.setDate(LocalDate.of(2026, 8, 20));
        confirmRequest.setTaxDeductible(false);
        confirmRequest.setReimbursable(false);

        ResponseEntity<?> confirmResponse = controller.confirm(
                receiptId, confirmRequest, authentication, httpServletRequest);
        assertThat(confirmResponse.getStatusCode().value()).isEqualTo(200);

        assertThat(expenseCaptor.getValue().getReceiptFileId())
                .isEqualTo(fileId);
        assertThat(expenseCaptor.getValue().getWalletId()).isEqualTo(walletId);

        ReceiptUpload confirmed = store.get(receiptId);
        assertThat(confirmed.getStatus()).isEqualTo(ReceiptStatus.CONFIRMED);
        assertThat(confirmed.getResultingTransactionType())
                .isEqualTo(ReceiptTransactionType.EXPENSE);
        assertThat(confirmed.getResultingTransactionId()).isEqualTo(expenseId);
        verify(serviceIncome, never()).newIncome(any(), any());
    }

    @Test
    void confirmCanOverrideSuggestedTypeToIncome() throws Exception {
        UUID receiptId = registerReceipt();
        driveToExtracted(receiptId);

        IncomeResponse incomeResponse = new IncomeResponse();
        UUID incomeId = UUID.randomUUID();
        incomeResponse.setId(incomeId.toString());
        ArgumentCaptor<IncomeRequest> incomeCaptor =
                ArgumentCaptor.forClass(IncomeRequest.class);
        when(serviceIncome.newIncome(incomeCaptor.capture(), eq(userId)))
                .thenReturn(incomeResponse);

        ReceiptConfirmRequest confirmRequest = new ReceiptConfirmRequest();
        confirmRequest.setType(ReceiptTransactionType.INCOME);
        confirmRequest.setTitle("Refund");
        confirmRequest.setAmount(new BigDecimal("10.00"));
        confirmRequest.setCategoryId(categoryId);
        confirmRequest.setDate(LocalDate.of(2026, 8, 20));
        confirmRequest.setTaxable(true);

        controller.confirm(
                receiptId, confirmRequest, authentication, httpServletRequest);

        assertThat(incomeCaptor.getValue().getReceiptFileId()).isEqualTo(fileId);
        assertThat(store.get(receiptId).getResultingTransactionType())
                .isEqualTo(ReceiptTransactionType.INCOME);
        assertThat(store.get(receiptId).getResultingTransactionId())
                .isEqualTo(incomeId);
        verify(serviceExpense, never()).newExpense(any(), any());
    }

    // ---- idempotency / redelivery --------------------------------------

    @Test
    void redeliveredOcrRequestMessageIsIdempotentOnceProcessing() {
        UUID receiptId = registerReceipt();
        when(textractClient.startExpenseAnalysis(
                any(StartExpenseAnalysisRequest.class)))
                .thenReturn(StartExpenseAnalysisResponse.builder()
                        .jobId("job-123")
                        .build());

        invokeHandle(requestListener, requestMessage(receiptId));
        invokeHandle(requestListener, requestMessage(receiptId));

        verify(textractClient, times(1))
                .startExpenseAnalysis(any(StartExpenseAnalysisRequest.class));
        assertThat(store.get(receiptId).getStatus())
                .isEqualTo(ReceiptStatus.PROCESSING);
    }

    @Test
    void redeliveredSnsCompletionIsIdempotentOnceExtracted() throws Exception {
        UUID receiptId = registerReceipt();
        driveToProcessing(receiptId, "job-123");

        when(textractClient.getExpenseAnalysis(
                any(GetExpenseAnalysisRequest.class)))
                .thenReturn(textractSuccessResponse());

        String body = snsCompletionBody("job-123", "SUCCEEDED");
        invokeHandle(resultListener, resultMessage(body));
        invokeHandle(resultListener, resultMessage(body));

        verify(textractClient, times(1))
                .getExpenseAnalysis(any(GetExpenseAnalysisRequest.class));
        assertThat(store.get(receiptId).getStatus())
                .isEqualTo(ReceiptStatus.EXTRACTED);
    }

    // ---- failure paths --------------------------------------------------

    @Test
    void textractJobFailureMarksReceiptFailedAndBlocksConfirm() throws Exception {
        UUID receiptId = registerReceipt();
        driveToProcessing(receiptId, "job-123");

        boolean handled = invokeHandle(resultListener,
                resultMessage(snsCompletionBody("job-123", "FAILED")));

        assertThat(handled).isTrue();
        ReceiptUpload failed = store.get(receiptId);
        assertThat(failed.getStatus()).isEqualTo(ReceiptStatus.FAILED);
        assertThat(failed.getFailureReason()).isNotBlank();
        verify(textractClient, never())
                .getExpenseAnalysis(any(GetExpenseAnalysisRequest.class));

        ReceiptConfirmRequest confirmRequest = new ReceiptConfirmRequest();
        confirmRequest.setType(ReceiptTransactionType.EXPENSE);
        confirmRequest.setTitle("Anything");
        confirmRequest.setAmount(BigDecimal.ONE);
        confirmRequest.setCategoryId(categoryId);
        confirmRequest.setDate(LocalDate.now());

        assertThatThrownBy(() -> controller.confirm(
                receiptId, confirmRequest, authentication, httpServletRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void textractWithNoExpenseDocumentsExtractsWithNullSuggestions()
            throws Exception {
        UUID receiptId = registerReceipt();
        driveToProcessing(receiptId, "job-123");

        when(textractClient.getExpenseAnalysis(
                any(GetExpenseAnalysisRequest.class)))
                .thenReturn(GetExpenseAnalysisResponse.builder()
                        .expenseDocuments(List.of())
                        .build());

        invokeHandle(resultListener,
                resultMessage(snsCompletionBody("job-123", "SUCCEEDED")));

        ReceiptUpload extracted = store.get(receiptId);
        assertThat(extracted.getStatus()).isEqualTo(ReceiptStatus.EXTRACTED);
        assertThat(extracted.getSuggestedTitle()).isNull();
        assertThat(extracted.getSuggestedAmount()).isNull();
        assertThat(extracted.getSuggestedDate()).isNull();
        assertThat(extracted.getSuggestedType())
                .isEqualTo(ReceiptTransactionType.EXPENSE);
    }

    @Test
    void malformedSnsEnvelopeIsDroppedWithoutAdvancingStatus() {
        UUID receiptId = registerReceipt();
        driveToProcessing(receiptId, "job-123");

        boolean handled = invokeHandle(
                resultListener, resultMessage("{not valid json"));

        assertThat(handled).isTrue(); // dropped, not redelivered
        assertThat(store.get(receiptId).getStatus())
                .isEqualTo(ReceiptStatus.PROCESSING);
        verify(textractClient, never())
                .getExpenseAnalysis(any(GetExpenseAnalysisRequest.class));
    }

    @Test
    void transientTextractApiFailureIsNotSwallowed() throws Exception {
        UUID receiptId = registerReceipt();
        driveToProcessing(receiptId, "job-123");

        when(textractClient.getExpenseAnalysis(
                any(GetExpenseAnalysisRequest.class)))
                .thenThrow(AwsServiceException.builder()
                        .message("throttled").build());

        // The result processor deliberately leaves this exception
        // uncaught so the SQS message is NOT deleted and gets
        // redelivered — assert it propagates rather than being
        // swallowed into a false "FAILED" transition.
        assertThatThrownBy(() -> invokeHandle(resultListener,
                resultMessage(snsCompletionBody("job-123", "SUCCEEDED"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(store.get(receiptId).getStatus())
                .isEqualTo(ReceiptStatus.PROCESSING);
    }

    // ---- discard ----------------------------------------------------------

    @Test
    void discardBeforeExtractionDeletesFileAndBlocksLateConfirm() {
        UUID receiptId = registerReceipt();

        ResponseEntity<?> response = controller.discard(
                receiptId, authentication, httpServletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(store.get(receiptId).getStatus())
                .isEqualTo(ReceiptStatus.DISCARDED);
        verify(storageService).delete(storedFile.getObjectKey());

        ReceiptConfirmRequest confirmRequest = new ReceiptConfirmRequest();
        confirmRequest.setType(ReceiptTransactionType.EXPENSE);
        confirmRequest.setTitle("Anything");
        confirmRequest.setAmount(BigDecimal.ONE);
        confirmRequest.setCategoryId(categoryId);
        confirmRequest.setDate(LocalDate.now());

        assertThatThrownBy(() -> controller.confirm(
                receiptId, confirmRequest, authentication, httpServletRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISCARDED");
    }

    @Test
    void discardingAConfirmedReceiptIsRejectedAndFileIsKept() throws Exception {
        UUID receiptId = registerReceipt();
        driveToExtracted(receiptId);

        ExpenseResponse expenseResponse = new ExpenseResponse();
        expenseResponse.setId(UUID.randomUUID().toString());
        when(serviceExpense.newExpense(any(), eq(userId)))
                .thenReturn(expenseResponse);

        ReceiptConfirmRequest confirmRequest = new ReceiptConfirmRequest();
        confirmRequest.setType(ReceiptTransactionType.EXPENSE);
        confirmRequest.setTitle("Anything");
        confirmRequest.setAmount(BigDecimal.ONE);
        confirmRequest.setCategoryId(categoryId);
        confirmRequest.setDate(LocalDate.now());
        controller.confirm(
                receiptId, confirmRequest, authentication, httpServletRequest);

        assertThatThrownBy(() -> controller.discard(
                receiptId, authentication, httpServletRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed");
        verify(storageService, never()).delete(anyString());
    }

    // ---- ownership ----------------------------------------------------------

    @Test
    void anotherUsersReceiptCannotBePolledOrConfirmed() {
        UUID receiptId = registerReceipt();
        UUID otherUserId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(authentication))
                .thenReturn(otherUserId);

        assertThatThrownBy(() -> controller.getStatus(
                receiptId, authentication, httpServletRequest))
                .isInstanceOf(FileAccessDeniedException.class);
    }

    // ---- helpers --------------------------------------------------------

    private UUID registerReceipt() {
        ReceiptUploadRequest request = new ReceiptUploadRequest();
        request.setFileId(fileId);
        request.setWalletId(walletId);

        ResponseEntity<?> response = controller.register(
                request, authentication, httpServletRequest);
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        // Exactly one receipt was persisted; recover its id from the
        // in-memory store rather than reaching into the ApiResponse
        // body's generics.
        assertThat(store).hasSize(1);
        return store.keySet().iterator().next();
    }

    private void driveToProcessing(final UUID receiptId, final String jobId) {
        when(textractClient.startExpenseAnalysis(
                any(StartExpenseAnalysisRequest.class)))
                .thenReturn(StartExpenseAnalysisResponse.builder()
                        .jobId(jobId)
                        .build());
        invokeHandle(requestListener, requestMessage(receiptId));
    }

    private void driveToExtracted(final UUID receiptId) throws Exception {
        driveToProcessing(receiptId, "job-123");
        when(textractClient.getExpenseAnalysis(
                any(GetExpenseAnalysisRequest.class)))
                .thenReturn(textractSuccessResponse());
        invokeHandle(resultListener,
                resultMessage(snsCompletionBody("job-123", "SUCCEEDED")));
    }

    /**
     * Invokes the package-private/protected {@code handle(Message)}
     * method a polling worker would otherwise only run from its own
     * background thread, simulating a message becoming visible.
     */
    private boolean invokeHandle(final Object listener, final Message message) {
        return (boolean) ReflectionTestUtils.invokeMethod(
                listener, "handle", message);
    }
}
