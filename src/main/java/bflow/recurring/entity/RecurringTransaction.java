package bflow.recurring.entity;

import bflow.auth.entities.User;
import bflow.category.entity.Category;
import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.wallet.entities.Wallet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
public class RecurringTransaction {
    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringType type; // EXPENSE | INCOME

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String title;

    @Column(length = 150)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    @Column(nullable = false)
    private Integer intervalValue = 1;

    @Column(nullable = false)
    private LocalDate nextExecutionDate;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
