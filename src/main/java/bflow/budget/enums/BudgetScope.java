package bflow.budget.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Budget scope enumeration.
 */
@Schema(description = "Budget coverage: WALLET covers all wallet categories; "
        + "WALLET_CATEGORY covers one category in one wallet; CATEGORY_GLOBAL "
        + "covers one category across eligible wallets.", allowableValues = {
        "WALLET", "WALLET_CATEGORY", "CATEGORY_GLOBAL"})
public enum BudgetScope {
    /** Budget scoped to an entire wallet, all categories. */
    WALLET,
    /** Budget scoped to a category within a specific wallet. */
    WALLET_CATEGORY,
    /** Budget scoped to a category across every wallet the user
     *  participates in, regardless of role. */
    CATEGORY_GLOBAL
}
