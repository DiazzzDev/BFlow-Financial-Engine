package Diaz.Dev.BFlow.gmail;

import bflow.auth.entities.User;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.gmail.service.GmailApiClient;
import bflow.gmail.service.GmailExpenseRegistrar;
import bflow.gmail.service.GmailImportProcessor;
import bflow.gmail.service.GmailImportQueuePublisher;
import bflow.gmail.service.GmailImportStateService;
import bflow.gmail.service.GmailOAuthService;
import bflow.gmail.service.GmailEmailTransactionParser;
import bflow.common.aws.service.StorageService;
import bflow.receipts.service.TextractExpenseJobStarter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import bflow.receipts.enums.ReceiptTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GmailImportProcessorTest {
    @Mock private GmailImportRepository importRepository;
    @Mock private GmailIntegrationRepository integrationRepository;
    @Mock private GmailImportStateService stateService;
    @Mock private GmailOAuthService oauthService;
    @Mock private GmailApiClient gmailApiClient;
    @Mock private StorageService storageService;
    @Mock private TextractExpenseJobStarter textractJobStarter;
    @Mock private GmailExpenseRegistrar expenseRegistrar;
    @Mock private GmailEmailTransactionParser emailTransactionParser;

    private GmailImportProcessor processor;
    private GmailImport item;
    private GmailIntegration integration;
    private final UUID importId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID integrationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new GmailImportProcessor(importRepository, integrationRepository,
                stateService, oauthService, gmailApiClient, storageService,
                textractJobStarter, expenseRegistrar, emailTransactionParser);
        item = new GmailImport();
        item.setId(importId);
        item.setUser(new User());
        item.getUser().setId(userId);
        item.setMessageId("message");
        item.setAttachmentId("attachment");
        integration = new GmailIntegration();
        integration.setId(integrationId);
        integration.setUser(item.getUser());
        integration.setStatus("CONNECTED");
    }

    @Test
    void rejectsMimeSignatureMismatchBeforeTextract() throws Exception {
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(item));
        when(stateService.claim(importId)).thenReturn(true);
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.downloadAttachment("access-token", "message", "attachment"))
                .thenReturn(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});

        processor.process(new GmailImportQueuePublisher.ImportJob(importId, userId,
                UUID.randomUUID(), integrationId, "message", "attachment", "image/png"));

        verify(stateService).markFailed(importId, "ATTACHMENT_MIME_INVALID");
        verify(textractJobStarter, never()).start(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(expenseRegistrar, never()).register(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void idempotentQueueRedeliveryDoesNotFetchGmailOrRunTextractAgain() throws Exception {
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(item));
        when(stateService.claim(importId)).thenReturn(false);

        processor.process(new GmailImportQueuePublisher.ImportJob(importId, userId,
                UUID.randomUUID(), integrationId, "message", "attachment", "image/png"));

        verify(gmailApiClient, never()).downloadAttachment(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(textractJobStarter, never()).start(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void uploadsValidPdfForSharedAsyncTextractAndZeroesDownloadedBytes() throws Exception {
        byte[] pdf = "%PDF-1.7 sample".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubClaimedImport(pdf);
        when(textractJobStarter.start("tmp/gmail/" + importId + "/source.pdf", importId.toString()))
                .thenReturn("textract-job");

        processor.process(job("application/pdf"));

        verify(storageService).upload(org.mockito.ArgumentMatchers.eq("tmp/gmail/" + importId + "/source.pdf"),
                any(), org.mockito.ArgumentMatchers.eq((long) pdf.length),
                org.mockito.ArgumentMatchers.eq("application/pdf"));
        verify(stateService).markProcessing(importId, "tmp/gmail/" + importId + "/source.pdf", "textract-job");
        assertThat(pdf).containsOnly((byte) 0);
    }

    @Test
    void registersTransactionalEmailBodyWithoutSendingItToTextract() throws Exception {
        byte[] body = "Payment received. Amount: USD 12.50".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubClaimedImport(body);
        when(emailTransactionParser.parse(body)).thenReturn(new GmailEmailTransactionParser.ParsedEmailTransaction(
                ReceiptTransactionType.INCOME, "Payroll", new BigDecimal("12.50"), LocalDate.of(2026, 11, 9)));

        processor.process(job("text/plain"));

        verify(expenseRegistrar).register(importId, "Payroll", new BigDecimal("12.50"),
                LocalDate.of(2026, 11, 9), ReceiptTransactionType.INCOME);
        verify(textractJobStarter, never()).start(anyString(), anyString());
        verify(storageService, never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        assertThat(body).containsOnly((byte) 0);
    }

    @Test
    void rejectsNonTransactionalEmailBodyAsPermanentFailure() throws Exception {
        byte[] body = "Your monthly newsletter".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubClaimedImport(body);
        when(emailTransactionParser.parse(body)).thenReturn(null);

        processor.process(job("text/plain"));

        verify(stateService).markFailed(importId, "EMAIL_NOT_TRANSACTION");
        verify(textractJobStarter, never()).start(anyString(), anyString());
        assertThat(body).containsOnly((byte) 0);
    }

    @Test
    void rejectsEmptyAttachmentAndMismatchedIntegrationOwner() throws Exception {
        stubClaimedImport(new byte[0]);

        processor.process(job("image/png"));

        verify(stateService).markFailed(importId, "ATTACHMENT_SIZE_INVALID");
        verify(storageService, never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());

        GmailIntegration otherUsersIntegration = new GmailIntegration();
        otherUsersIntegration.setUser(new User());
        otherUsersIntegration.getUser().setId(UUID.randomUUID());
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(item));
        when(stateService.claim(importId)).thenReturn(true);
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(otherUsersIntegration));

        processor.process(job("image/png"));

        verify(stateService).markFailed(importId, "GMAIL_DISCONNECTED");
        verify(gmailApiClient).downloadAttachment("access-token", "message", "attachment");
    }

    @Test
    void removesUploadedStagingObjectAndPreservesTextractFailureWhenCleanupAlsoFails() throws Exception {
        byte[] pdf = "%PDF-1.7 sample".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubClaimedImport(pdf);
        IllegalStateException startFailure = new IllegalStateException("Textract unavailable");
        when(textractJobStarter.start(anyString(), anyString())).thenThrow(startFailure);
        doThrow(new IllegalStateException("S3 delete unavailable"))
                .when(storageService).delete("tmp/gmail/" + importId + "/source.pdf");

        assertThatThrownBy(() -> processor.process(job("application/pdf"))).isSameAs(startFailure);

        assertThat(startFailure.getSuppressed()).hasSize(1);
        assertThat(pdf).containsOnly((byte) 0);
        verify(storageService).delete("tmp/gmail/" + importId + "/source.pdf");
        verify(stateService, never()).markFailed(importId, "TEXTRACT_FAILED");
    }

    @Test
    void rethrowsTransientGmailFailureForQueueRetry() throws Exception {
        stubClaimedImport(null);
        when(gmailApiClient.downloadAttachment("access-token", "message", "attachment"))
                .thenThrow(new IllegalStateException("temporary Gmail failure"));

        assertThatThrownBy(() -> processor.process(job("image/png")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary Gmail failure");

        verify(stateService, never()).markFailed(any(), anyString());
    }

    private void stubClaimedImport(byte[] bytes) throws Exception {
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(item));
        when(stateService.claim(importId)).thenReturn(true);
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.downloadAttachment("access-token", "message", "attachment")).thenReturn(bytes);
    }

    private GmailImportQueuePublisher.ImportJob job(String mimeType) {
        return new GmailImportQueuePublisher.ImportJob(importId, userId,
                UUID.randomUUID(), integrationId, "message", "attachment", mimeType);
    }

}
