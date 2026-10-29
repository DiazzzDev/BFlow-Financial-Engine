package bflow.receipts.service;

import bflow.receipts.entity.ReceiptUpload;
import bflow.receipts.enums.ReceiptStatus;
import bflow.receipts.repository.RepositoryReceiptUpload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisResponse;
import software.amazon.awssdk.services.textract.model.ExpenseField;

import java.util.Optional;
import java.util.Set;

/**
 * Handles a Textract job-completion notification: fetches the full
 * result and maps it into the receipt's suggested draft, or records
 * the failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptOcrResultProcessor {

    /** Status value Textract sends when the job failed outright. */
    private static final String STATUS_FAILED = "FAILED";

    /** Textract summary signals that distinguish a receipt from arbitrary media. */
    private static final Set<String> RECEIPT_FIELD_TYPES = Set.of(
            "VENDOR_NAME", "TOTAL", "INVOICE_RECEIPT_DATE",
            "SUBTOTAL", "TAX", "RECEIPT_ID"
    );

    /** Repository for receipt upload records. */
    private final RepositoryReceiptUpload repositoryReceiptUpload;

    /** Persists the EXTRACTED/FAILED transition in its own transaction. */
    private final ReceiptStatusTransitionService statusTransitionService;

    /** Textract client used to fetch the full job result. */
    private final TextractClient textractClient;

    /** Maps Textract's response into the receipt's suggested draft. */
    private final TextractExpenseMapper textractExpenseMapper;

    /**
     * Processes a completion notification for a Textract job.
     *
     * <p>Not every job that lands here belongs to a receipt still
     * waiting on it: an already-{@code EXTRACTED} or {@code FAILED}
     * receipt means this is a redelivered SNS notification (SNS/SQS
     * is at-least-once), and is skipped rather than reprocessed.</p>
     *
     * <p>Known limitation: only the first page of {@code
     * GetExpenseAnalysis} results is fetched. Pagination via {@code
     * NextToken} isn't implemented — fine for single-page receipts
     * and invoices, not for very large multi-document files.</p>
     *
     * @param jobId the Textract JobId the notification refers to
     * @param status the job status Textract reported
     *         ({@code SUCCEEDED}, {@code FAILED} or {@code
     *         PARTIAL_SUCCESS})
     */
    public void processCompletion(final String jobId, final String status) {
        Optional<ReceiptUpload> maybeReceipt =
                repositoryReceiptUpload.findByTextractJobId(jobId);

        if (maybeReceipt.isEmpty()) {
            log.warn(
                    "No receipt found for Textract job {}; "
                            + "notification ignored",
                    jobId
            );
            return;
        }

        ReceiptUpload receipt = maybeReceipt.get();

        if (receipt.getStatus() != ReceiptStatus.PROCESSING) {
            log.info(
                    "Skipping completion for job {}: receipt {} is "
                            + "already {}",
                    jobId, receipt.getId(), receipt.getStatus()
            );
            return;
        }

        if (STATUS_FAILED.equals(status)) {
            statusTransitionService.markFailed(
                receipt.getId(), "Textract could not process this document");
            return;
        }

        // SUCCEEDED or PARTIAL_SUCCESS: fetch and map the result.
        // A thrown exception here is left uncaught on purpose — the
        // caller (ReceiptOcrResultListener) treats it as transient
        // and leaves the SQS message for redelivery, rather than
        // this method silently marking a receipt FAILED for what
        // might just be a momentary Textract/API hiccup.
        GetExpenseAnalysisResponse response = textractClient
                .getExpenseAnalysis(GetExpenseAnalysisRequest.builder()
                        .jobId(jobId)
                        .build());

        boolean hasReceiptSignals = response.expenseDocuments().stream()
                .flatMap(document -> document.summaryFields().stream())
                .map(ExpenseField::type)
                .filter(java.util.Objects::nonNull)
                .map(type -> type.text())
                .anyMatch(RECEIPT_FIELD_TYPES::contains);

        if (!hasReceiptSignals) {
            statusTransitionService.markFailed(
                    receipt.getId(), "No receipt fields detected");
            log.info("Textract job {} did not contain receipt fields", jobId);
            return;
        }

        statusTransitionService.markExtracted(
                receipt.getId(), textractExpenseMapper.map(response));
    }
}
