package bflow.subscription.entities;

import bflow.auth.entities.User;
import bflow.subscription.enums.SubscriptionStatus;
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
        name = "subscriptions",
        indexes = {
                @Index(name = "idx_subscription_user", columnList = "user_id"),
                @Index(name = "idx_subscription_status", columnList = "status"),
                @Index(
                        name = "idx_subscription_next_billing",
                        columnList = "next_billing_at"
                )
        }
)
@Getter
@Setter
public class Subscription {

    /** Precision used for money columns. */
    private static final int MONEY_PRECISION = 10;

    /** Primary key for the subscription entity. */
    @Id
    @GeneratedValue
    private UUID id;

    /** User who owns the subscription. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Plan associated with the subscription. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    /** Current subscription status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    /** Current price charged for the subscription. */
    @Column(nullable = false, precision = MONEY_PRECISION, scale = 2)
    private BigDecimal billingAmount;

    /** When the subscription starts. */
    @Column(nullable = false)
    private Instant startsAt;

    /** When the subscription ends. */
    @Column
    private Instant endsAt;

    /** Whether the subscription renews automatically. */
    @Column(nullable = false)
    private boolean autoRenew = true;

    /** Wompi recurring payment link identifier for the subscription. */
    @Column(name = "provider_link_id")
    private String providerLinkId;

    /** Checkout URL returned by Wompi for this subscription. */
    @Column(name = "checkout_url")
    private String checkoutUrl;

    /** Reference used to match one-time annual payment webhooks. */
    @Column(name = "checkout_reference")
    private String checkoutReference;

    /** Day of the month selected for payment processing. */
    @Column(name = "billing_day")
    private Integer billingDay;

    /** Provider subscriber identifier assigned by Wompi. */
    @Column(name = "provider_subscriber_id")
    private String providerSubscriberId;

    /** When the subscription was canceled, if applicable. */
    @Column
    private Instant canceledAt;

    /**
     * Timestamp when the last renewal reminder was sent.
     * Used for annual plans.
     */
    @Column
    private Instant reminderSentAt;

    /** Timestamp when the subscription record was created. */
    @CreationTimestamp
    private Instant createdAt;

    /** Timestamp when the subscription record was last updated. */
    @UpdateTimestamp
    private Instant updatedAt;

    /** Next billing date for the subscription. */
    @Column(name = "next_billing_at")
    private Instant nextBillingAt;
}

