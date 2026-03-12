package bflow.wallet.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import java.math.BigDecimal;
import java.time.Instant;
import bflow.wallet.enums.Currency;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a financial wallet.
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class Wallet {

    /** The unique identifier for the wallet. */
    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** The display name of the wallet. */
    @Column(nullable = false)
    private String name;

    /** The display name of the wallet. */
    @Column(nullable = false)
    private String description;

    /** The currency code (e.g., USD, EUR). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency; // "USD", "EUR"

    /** The current available balance. */
    @Column(nullable = false)
    private BigDecimal balance;

    /** The balance the wallet started with. */
    @Column(nullable = false, updatable = false)
    private BigDecimal initialValue;

    /** The timestamp when the wallet was created. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** The timestamp when the wallet was updated. */
    @UpdateTimestamp
    private Instant updatedAt;

}
