package bflow.receipts.DTO;

import bflow.receipts.enums.ReceiptStatus;
import bflow.receipts.enums.ReceiptTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable response returned after a receipt upload, confirmation or read. */
public record ReceiptUploadResponse(
        UUID id,
        UUID fileId,
        UUID walletId,
        ReceiptStatus status,
        ReceiptTransactionType suggestedType,
        String suggestedTitle,
        BigDecimal suggestedAmount,
        UUID suggestedCategoryId,
        LocalDate suggestedDate,
        BigDecimal confidenceScore,
        String failureReason,
        UUID resultingExpenseId,
        Instant createdAt
) {
    public UUID getId() { return id; }
    public UUID getFileId() { return fileId; }
    public UUID getWalletId() { return walletId; }
    public ReceiptStatus getStatus() { return status; }
    public ReceiptTransactionType getSuggestedType() { return suggestedType; }
    public String getSuggestedTitle() { return suggestedTitle; }
    public BigDecimal getSuggestedAmount() { return suggestedAmount; }
    public UUID getSuggestedCategoryId() { return suggestedCategoryId; }
    public LocalDate getSuggestedDate() { return suggestedDate; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public String getFailureReason() { return failureReason; }
    public UUID getResultingExpenseId() { return resultingExpenseId; }
    public Instant getCreatedAt() { return createdAt; }
}
