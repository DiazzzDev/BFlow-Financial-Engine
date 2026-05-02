package bflow.income.entity;

import bflow.common.financial.Transaction;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

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
     * Whether this income is taxable.
     */
    @Column
    private Boolean taxable;

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
}
