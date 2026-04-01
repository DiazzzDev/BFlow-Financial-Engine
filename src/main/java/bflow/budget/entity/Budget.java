package bflow.budget.entity;

import bflow.auth.entities.User;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.budget.enums.PeriodType;
import bflow.wallet.entities.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Budget entity class.
 */
@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
public final class Budget {
    /**
     * The budget ID.
     */
    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * The associated wallet.
     */
    @ManyToOne
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    /**
     * The associated user.
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The budget period type.
     */
    @Enumerated(EnumType.STRING)
    private PeriodType period;

    /**
     * The budget amount.
     */
    private BigDecimal amount;

    /**
     * The warning threshold percentage.
     */
    private Integer thresholdWarning;

    /**
     * The critical threshold percentage.
     */
    private Integer thresholdCritical;

    /**
     * The start date of the budget period.
     */
    private LocalDate startDate;

    /**
     * The budget scope (WALLET or CATEGORY).
     */
    @Enumerated(EnumType.STRING)
    private BudgetScope scope;

    /**
     * The category ID (if scope is CATEGORY).
     */
    private UUID categoryId;

    /**
     * The current status of the budget.
     */
    @Enumerated(EnumType.STRING)
    private BudgetStatus lastAlertStatus;

    /**
     * The creation timestamp.
     */
    @CreationTimestamp
    private Instant createdAt;

    /**
     * The last update timestamp.
     */
    @UpdateTimestamp
    private Instant updatedAt;
}
