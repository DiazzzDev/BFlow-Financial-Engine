package bflow.recurring.entity;

import bflow.auth.entities.User;
import bflow.category.entity.Category;
import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.wallet.entities.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recurring transaction entity.
 */
@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
public final class RecurringTransaction {

    /**
     * Minimum interval value.
     */
    private static final int MIN_INTERVAL = 1;
    /**
     * Maximum title length.
     */
    private static final int MAX_TITLE_LENGTH = 255;
    /**
     * Maximum description length.
     */
    private static final int MAX_DESC_LENGTH = 150;
    /**
     * Default interval value.
     */
    private static final int DEFAULT_INTERVAL = 1;
    /**
     * Decimal precision.
     */
    private static final int DECIMAL_PRECISION = 15;
    /**
     * Decimal scale.
     */
    private static final int DECIMAL_SCALE = 2;

    /**
     * The recurring transaction ID.
     */
    @Id
    @GeneratedValue
    private UUID id;

    /**
     * The recurring type (EXPENSE or INCOME).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringType type;

    /**
     * The transaction amount.
     */
    @Column(nullable = false, precision = DECIMAL_PRECISION,
            scale = DECIMAL_SCALE)
    private BigDecimal amount;

    /**
     * The transaction title.
     */
    @Column(nullable = false)
    private String title;

    /**
     * The transaction description.
     */
    @Column(length = MAX_DESC_LENGTH)
    private String description;

    /**
     * The recurring frequency.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    /**
     * The interval value for custom frequencies.
     */
    @Column(nullable = false)
    private Integer intervalValue = DEFAULT_INTERVAL;

    /**
     * The next execution date.
     */
    @Column(nullable = false)
    private LocalDate nextExecutionDate;

    /**
     * The start date of the recurring transaction.
     */
    @Column(nullable = false)
    private LocalDate startDate;

    /**
     * The end date of the recurring transaction.
     */
    private LocalDate endDate;

    /**
     * Whether the recurring transaction is active.
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * The associated wallet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    /**
     * The associated category.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * The associated user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
