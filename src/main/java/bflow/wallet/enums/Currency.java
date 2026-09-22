package bflow.wallet.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of currencies a user can select within a specific wallet.
 */
@Schema(description = "Currency supported by wallets and budgets.",
        allowableValues = {"USD", "EUR", "MXN"})
public enum Currency {
    /** US main currency. */
    USD,

    /** European main currency. */
    EUR,

    /** Mexican main currency. */
    MXN
}
