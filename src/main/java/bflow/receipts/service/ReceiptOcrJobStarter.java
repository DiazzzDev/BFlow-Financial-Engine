package bflow.receipts.service;

import bflow.receipts.entity.ReceiptUpload;
import bflow.receipts.enums.ReceiptStatus;
import bflow.receipts.repository.RepositoryReceiptUpload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Submits the async Textract job for a receipt. Called by {@code
 * ReceiptOcrRequestListener} after popping a message off the OCR
 * request queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptOcrJobStarter {

    /** Repository for receipt upload records. */
    private final RepositoryReceiptUpload repositoryReceiptUpload;

    /** Persists the PROCESSING transition in its own transaction. */
    private final ReceiptStatusTransitionService statusTransitionService;

    /** Shared S3-backed asynchronous Textract submission service. */
    private final TextractExpenseJobStarter textractExpenseJobStarter;

    /**
     * Starts an {@code AnalyzeExpense} job for the given receipt and
     * records the returned JobId.
     *
     * <p>Idempotent at the business level: if the receipt is no
     * longer {@code RECEIVED} (e.g. this is a redelivered SQS
     * message for a receipt already submitted), the call is a
     * no-op. Idempotent at the Textract level too: {@code
     * clientRequestToken} is the receipt id, so even a genuine
     * double-submit within Textract's dedup window does not create
     * a second job.</p>
     *
     * @param receiptId the id of the receipt to process
     * @throws IllegalStateException if the receipt does not exist
     */
    public void startJob(final UUID receiptId) {
        ReceiptUpload receipt = repositoryReceiptUpload
                .findByIdWithStoredFile(receiptId)
                .orElseThrow(() -> new IllegalStateException(
                        "Receipt upload not found: " + receiptId));

        if (receipt.getStatus() != ReceiptStatus.RECEIVED) {
            log.info(
                    "Skipping OCR request for receipt {}: status is "
                            + "already {}",
                    receiptId, receipt.getStatus()
            );
            return;
        }

        String objectKey = receipt.getStoredFile().getObjectKey();

        String jobId = textractExpenseJobStarter.start(objectKey, receiptId.toString());

        statusTransitionService.markProcessing(
                receiptId, jobId);

        log.info(
                "Submitted Textract job {} for receipt {}",
                jobId, receiptId
        );
    }
}
