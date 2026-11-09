package bflow.gmail.service;

import bflow.common.aws.service.StorageService;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.receipts.service.TextractExpenseJobStarter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

/** Stages Gmail attachments in private S3 for the shared asynchronous Textract workflow. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmailImportProcessor {
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private final GmailImportRepository importRepository;
    private final GmailIntegrationRepository integrationRepository;
    private final GmailImportStateService stateService;
    private final GmailOAuthService oauthService;
    private final GmailApiClient gmailApiClient;
    private final StorageService storageService;
    private final TextractExpenseJobStarter textractJobStarter;
    private final GmailExpenseRegistrar expenseRegistrar;
    private final GmailEmailTransactionParser emailTransactionParser;

    public void process(final GmailImportQueuePublisher.ImportJob job) {
        GmailImport item = importRepository.findByIdAndUserId(job.importId(), job.userId()).orElse(null);
        if (item == null || !item.getMessageId().equals(job.messageId())
                || !item.getAttachmentId().equals(job.attachmentId()) || !stateService.claim(item.getId())) return;
        byte[] bytes = null;
        try {
            GmailIntegration integration = integrationRepository.findById(job.integrationId())
                    .filter(value -> value.getUser().getId().equals(job.userId()))
                    .filter(value -> "CONNECTED".equals(value.getStatus()))
                    .orElseThrow(() -> new PermanentImportException("GMAIL_DISCONNECTED"));
            bytes = gmailApiClient.downloadAttachment(oauthService.accessToken(integration),
                    job.messageId(), job.attachmentId());
            if (bytes.length == 0 || bytes.length > MAX_ATTACHMENT_BYTES) {
                throw new PermanentImportException("ATTACHMENT_SIZE_INVALID");
            }
            if (job.mimeType().startsWith("text/")) registerBodyTransaction(item, bytes);
            else {
                validateRealMime(bytes, job.mimeType());
                stageForAsyncTextract(item, bytes, job.mimeType());
            }
        } catch (PermanentImportException ex) {
            stateService.markFailed(item.getId(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Gmail import staging failed for import {}", job.importId());
            throw ex;
        } catch (Exception ex) {
            log.warn("Gmail import staging failed for import {}", job.importId());
            throw new IllegalStateException("Transient Gmail import staging failure");
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private void registerBodyTransaction(final GmailImport item, final byte[] bytes) {
        GmailEmailTransactionParser.ParsedEmailTransaction transaction = emailTransactionParser.parse(bytes);
        if (transaction == null) throw new PermanentImportException("EMAIL_NOT_TRANSACTION");
        expenseRegistrar.register(item.getId(), transaction.title(), transaction.amount(),
                transaction.date(), transaction.type());
    }

    private void stageForAsyncTextract(final GmailImport item, final byte[] bytes, final String mimeType) {
        String objectKey = "tmp/gmail/" + item.getId() + "/source" + extension(mimeType);
        boolean uploaded = false;
        try {
            storageService.upload(objectKey, new ByteArrayInputStream(bytes), bytes.length, mimeType);
            uploaded = true;
            String jobId = textractJobStarter.start(objectKey, item.getId().toString());
            stateService.markProcessing(item.getId(), objectKey, jobId);
        } catch (RuntimeException failure) {
            if (uploaded) cleanup(objectKey, failure);
            throw failure;
        }
    }

    private void cleanup(final String objectKey, final RuntimeException original) {
        try { storageService.delete(objectKey); }
        catch (RuntimeException cleanupFailure) { original.addSuppressed(cleanupFailure); }
    }

    private String extension(final String mimeType) {
        return switch (mimeType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> "";
        };
    }

    private void validateRealMime(final byte[] bytes, final String mimeType) {
        boolean pdf = bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        if (!(pdf && "application/pdf".equals(mimeType)
                || jpeg && "image/jpeg".equals(mimeType)
                || png && "image/png".equals(mimeType))) {
            throw new PermanentImportException("ATTACHMENT_MIME_INVALID");
        }
    }

    private static final class PermanentImportException extends RuntimeException {
        private PermanentImportException(final String code) { super(code); }
    }
}
