package bflow.subscription.entities;

import bflow.auth.entities.User;
import bflow.subscription.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payment_user", columnList = "user_id"),
                @Index(
                        name = "idx_payment_subscription",
                        columnList = "subscription_id"
                ),
                @Index(
                        name = "idx_payment_provider_payment",
                        columnList = "provider_payment_id"
                ),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(
                        name = "idx_payment_idempotency",
                        columnList = "idempotency_key",
                        unique = true
                )
        }
)
@Getter
@Setter
public class Payment {

    /** Precision used for money columns. */
    private static final int MONEY_PRECISION = 10;

    /** Maximum length for currency codes. */
    private static final int CURRENCY_LENGTH = 3;

    /** Maximum length for provider names. */
    private static final int PROVIDER_LENGTH = 30;

    /** Maximum length for failure reason text. */
    private static final int FAILURE_REASON_LENGTH = 500;

    /** Unique identifier for the payment. */
    @Id
    @GeneratedValue
    private UUID id;

    /** User who initiated the payment. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Subscription related to the payment. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    /** Amount charged for the payment. */
    @Column(nullable = false, precision = MONEY_PRECISION, scale = 2)
    private BigDecimal amount;

    /** Three-letter currency code. */
    @Column(nullable = false, length = CURRENCY_LENGTH)
    private String currency;

    /**
     * Payment provider such as WOMPI, STRIPE or PAYPAL.
     */
    @Column(nullable = false, length = PROVIDER_LENGTH)
    private String provider;

    /** Provider transaction identifier. */
    @Column(name = "provider_payment_id", unique = true)
    private String providerPaymentId;

    /** Reference submitted to the payment provider. */
    @Column(nullable = false, unique = true)
    private String reference;

    /** Key used to prevent duplicate payments. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    /** Current payment status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** Error message returned by the payment provider. */
    @Column(length = FAILURE_REASON_LENGTH)
    private String failureReason;

    /** Instant when the payment was confirmed. */
    private Instant processedAt;

    /** Timestamp when the payment record was created. */
    @CreationTimestamp
    private Instant createdAt;

    /** Timestamp when the payment record was last updated. */
    @UpdateTimestamp
    private Instant updatedAt;
}

