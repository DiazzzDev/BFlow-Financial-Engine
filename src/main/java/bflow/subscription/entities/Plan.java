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

    @Column(nullable = false, unique = true, length = PLAN_CODE_LENGTH)
    private String code;

    @Column(nullable = false, length = PLAN_NAME_LENGTH)
    private String name;

    @Column(nullable = false, precision = MONEY_PRECISION, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingPeriod billingPeriod;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private String providerLinkId;

    @Column
    private String checkoutUrl;

    @Column
    private Integer billingDay;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
