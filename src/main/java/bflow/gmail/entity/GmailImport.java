package bflow.gmail.entity;

import bflow.auth.entities.User;
import bflow.wallet.entities.Wallet;
import bflow.receipts.enums.ReceiptTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_imports")
@Getter
@Setter
public class GmailImport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    private GmailIntegration integration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    /** Database-level idempotency key: one financial decision per Gmail message. */
    @Column(name = "message_dedup_key", unique = true, length = 512)
    private String messageDedupKey;

    @Column(name = "attachment_id", nullable = false, length = 512)
    private String attachmentId;

    /** Private, short-lived S3 object used only while async Textract runs. */
    @Column(name = "s3_object_key", length = 512)
    private String s3ObjectKey;

    @Column(name = "textract_job_id", length = 128, unique = true)
    private String textractJobId;

    @Column(nullable = false)
    private String status = "QUEUED";

    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    private ReceiptTransactionType transactionType;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
