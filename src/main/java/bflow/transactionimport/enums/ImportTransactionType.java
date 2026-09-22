package bflow.transactionimport.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Transaction types accepted by transaction import (any source
 * format). Transfers are intentionally excluded.
 */
@Schema(description = "Transaction type accepted by the import flow.",
        allowableValues = {"EXPENSE", "INCOME"})
public enum ImportTransactionType {
    /** Expense row. */
    EXPENSE,

    /** Income row. */
    INCOME
}
