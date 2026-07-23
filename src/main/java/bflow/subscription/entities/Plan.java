package bflow.subscription.entities;

import bflow.subscription.enums.BillingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@Setter
public class Plan {
    /** Maximum length for the plan code column. */
    private static final int PLAN_CODE_LENGTH = 50;

    /** Maximum length for the plan name column. */
    private static final int PLAN_NAME_LENGTH = 100;

    /** Precision used for monetary columns. */
    private static final int MONEY_PRECISION = 10;

    /** Primary key for the plan. */
    @Id
    @GeneratedValue
    private UUID id;

    /** Unique code that identifies the plan. */
    @Column(nullable = false, unique = true, length = PLAN_CODE_LENGTH)
    private String code;

    /** Display name of the plan. */
    @Column(nullable = false, length = PLAN_NAME_LENGTH)
    private String name;

    /** Price charged for the plan. */
    @Column(nullable = false, precision = MONEY_PRECISION, scale = 2)
    private BigDecimal price;

    /** Billing cadence applied to the plan. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingPeriod billingPeriod;

    /** Whether the plan is currently active for purchase. */
    @Column(nullable = false)
    private boolean active = true;

    /** Provider identifier for recurring payment links. */
    @Column
    private String providerLinkId;

    /** Checkout URL returned by the payment provider. */
    @Column
    private String checkoutUrl;

    /** Billing day selected for monthly subscriptions. */
    @Column
    private Integer billingDay;

    /** Timestamp when the plan record was created. */
    @CreationTimestamp
    private Instant createdAt;

    /** Timestamp when the plan record was last updated. */
    @UpdateTimestamp
    private Instant updatedAt;
}

