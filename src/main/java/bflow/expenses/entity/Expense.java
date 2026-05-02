package bflow.expenses.entity;

import bflow.common.financial.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Expense extends Transaction {

    /**
     * Whether this expense is tax deductible.
     */
    @Column(nullable = false)
    private Boolean taxDeductible = false;

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

    /**
     * Indicates if the expense is reimbursable.
     */
    @Column(nullable = false)
    private Boolean reimbursable = false;

    /**
     * Indicates if this is a default expense.
     */
    @Column(nullable = false)
    private Boolean isDefault = false;
}
