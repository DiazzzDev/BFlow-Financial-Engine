package bflow.wallet.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the lifecycle of a wallet invitation.
 */
@Schema(description = "Wallet invitation lifecycle: PENDING awaits a reply; "
        + "ACCEPTED or REJECTED were answered; EXPIRED timed out; CANCELED "
        + "was withdrawn by the owner.", allowableValues = {"PENDING",
        "ACCEPTED", "REJECTED", "EXPIRED", "CANCELED"})
public enum WalletInvitationStatus {

    /**
     * The invitation has been created and is awaiting a response.
     */
    PENDING,

    /**
     * The invitation has been accepted by the recipient.
     */
    ACCEPTED,

    /**
     * The invitation has been declined by the recipient.
     */
    REJECTED,

    /**
     * The invitation expired before the recipient responded.
     */
    EXPIRED,

    /**
     * The invitation was canceled by the wallet owner.
     */
    CANCELED
}
