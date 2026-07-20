package bflow.subscription.entities;

import bflow.auth.entities.User;
import bflow.subscription.enums.PaymentStatus;
import jakarta.persistence.*;
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
                @Index(
                        name = "idx_payment_user", 
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_payment_subscription", 
                        columnList = "subscription_id"
                ),
                @Index(
                        name = "idx_payment_provider_payment", 
                        columnList = "provider_payment_id"
                ),
                @Index(
                        name = "idx_payment_status", 
                        columnList = "status"
                ),
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

    private static final int MONEY_PRECISION = 10;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(nullable = false, precision = MONEY_PRECISION, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * WOMPI
     * STRIPE
     * PAYPAL
     */
    @Column(nullable = false, length = 30)
    private String provider;

    /**
     * Id de la transacción en el proveedor.
     */
    @Column(name = "provider_payment_id", unique = true)
    private String providerPaymentId;

    /**
     * Referencia enviada al proveedor.
     */
    @Column(nullable = false, unique = true)
    private String reference;

    /**
     * Clave para evitar pagos duplicados.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /**
     * Mensaje de error del proveedor.
     */
    @Column(length = 500)
    private String failureReason;

    /**
     * Momento en que el pago quedó confirmado.
     */
    private Instant processedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
