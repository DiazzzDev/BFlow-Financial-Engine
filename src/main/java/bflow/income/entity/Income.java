package bflow.income.entity;

import bflow.common.financial.Transaction;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import bflow.storage.entity.StoredFile;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.util.UUID;

/**
 * Entity representing incomes in wallets.
 */
@Entity
@Table(name = "incomes")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Income extends Transaction {

    /**
     * Whether this income is a recurring income.
     */
    @Column
    private Boolean recurring;

    /**
     * The recurrence pattern for recurring income (e.g., MONTHLY, YEARLY).
     */
    @Column
    private String recurrencePattern;

    /** ID of the RecurringTransaction this entry is linked to, if any. */
    @Column(name = "recurring_transaction_id")
    private UUID recurringTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_file_id")
    private StoredFile receiptFile;
}
