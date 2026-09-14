package bflow.expenses.entity;

import bflow.common.financial.Transaction;
import bflow.storage.entity.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Expense extends Transaction {

    /**
     * Whether this expense is recurring.
     */
    @Column(nullable = false)
    private Boolean recurring = false;

    /**
     * Recurrence pattern (e.g., MONTHLY, YEARLY).
     */
    @Column
    private String recurrencePattern;

    /** ID of the RecurringTransaction this entry is linked to, if any. */
    @Column(name = "recurring_transaction_id")
    private UUID recurringTransactionId;

    /**
     * Indicates if this is a default expense.
     */
    @Column(nullable = false)
    private Boolean isDefault = false;

    /**
     * Receipt file associated with this expense.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_file_id")
    private StoredFile receiptFile;
}
