package bflow.receipts.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** Defines the transaction types that can be associated with a receipt. */
@Schema(description = "Transaction type created from a receipt.",
        allowableValues = {"EXPENSE", "INCOME"})
public enum ReceiptTransactionType {

    /** Represents an expense transaction. */
    EXPENSE,

    /** Represents an income transaction. */
    INCOME
}
