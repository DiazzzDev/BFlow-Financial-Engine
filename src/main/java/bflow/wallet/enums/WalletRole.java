package bflow.wallet.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration of roles a user can have within a specific wallet.
 */
@Schema(description = "Role of a user in a wallet.",
        allowableValues = {"OWNER", "MEMBER"})
public enum WalletRole {
    /** The creator and primary administrator of the wallet. */
    OWNER,

    /** A user with shared access to the wallet. */
    MEMBER
}
