package bflow.wallet.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filter scope for the wallet listing endpoint, based on how many
 * members a wallet has — independent of the caller's role (OWNER/MEMBER).
 */
@Schema(description = "Wallet list filter: MINE returns solo wallets and "
        + "SHARED returns wallets with multiple members.",
        allowableValues = {"MINE", "SHARED"})
public enum WalletScope {
    /** Wallets where the caller is the only member. */
    MINE,
    /** Wallets with more than one member (shared, regardless of role). */
    SHARED
}
