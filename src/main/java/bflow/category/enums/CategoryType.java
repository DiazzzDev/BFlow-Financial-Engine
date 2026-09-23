package bflow.category.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Financial direction represented by a category.",
        allowableValues = {"INCOME", "EXPENSE", "TRANSFER"})
public enum CategoryType {
    /**
     * Income category type for transactions that increase balance.
     */
    INCOME,

    /**
     * Expense category type for transactions that decrease balance.
     */
    EXPENSE,

    /**
     * Transfer category type for transactions between wallets.
     */
    TRANSFER
}
