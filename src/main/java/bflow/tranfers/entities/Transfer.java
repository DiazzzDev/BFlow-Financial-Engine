package bflow.tranfers.entities;

import bflow.tranfers.enums.TransferStatus;
import bflow.wallet.entities.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a transfer transaction between two wallets.
 */
@Entity
@Table(name = "transfer")
@Getter
@Setter
public class Transfer {
    /** The transfer identifier. */
    @Id
    @GeneratedValue
    private UUID id;

    /** The source wallet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id", nullable = false)
    private Wallet fromWalletId;

    /** The destination wallet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id", nullable = false)
    private Wallet toWalletId;

    /** The transferred amount. */
    @Column
    private BigDecimal amount;

    /** Optional transfer description. */
    @Column
    private String description;

    /** The transfer status. */
    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    /** The timestamp when the transfer was created. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
