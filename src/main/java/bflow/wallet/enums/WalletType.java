package bflow.wallet.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of the different types of financial containers supported.
 */
@Schema(description = "Financial account classification.", allowableValues = {
        "SAVINGS", "CHECKING_ACCOUNT", "CASH", "INVESTMENTS", "LOAN",
        "CREDIT_CARD", "DEBIT_CARD", "OTHER"})
public enum WalletType {
    /** Account for long-term savings. */
    SAVINGS,

    /** Standard bank checking account. */
    CHECKING_ACCOUNT,

    /** Physical currency. */
    CASH,

    /** Stocks, bonds, or other investment vehicles. */
    INVESTMENTS,

    /** Borrowed funds or liability account. */
    LOAN,

    /** Credit line account. */
    CREDIT_CARD,

    /** Account linked to a debit card. */
    DEBIT_CARD,

    /** Any other type of financial account. */
    OTHER
}
