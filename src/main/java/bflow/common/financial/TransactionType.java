package bflow.common.financial;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Discriminates the origin of a unified transaction history entry.
 */
@Schema(description = "Type of financial transaction.",
        allowableValues = {"INCOME", "EXPENSE", "TRANSFER"})
public enum TransactionType {

    /**
     * Money received by a wallet.
     */
    INCOME,

    /**
     * Money spent from a wallet.
     */
    EXPENSE,

    /**
     * Transfer of funds between wallets.
     */
    TRANSFER
}
