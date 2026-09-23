package bflow.wallet.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status of a user relative to a wallet, from the perspective of the
 * collaborator search endpoint.
 */
@Schema(description = "Whether a searched user can be invited to a wallet.",
        allowableValues = {"INVITABLE", "ALREADY_MEMBER", "INVITATION_PENDING"})
public enum CollaboratorStatus {

    /** The user can be invited to the wallet. */
    INVITABLE,

    /** The user already belongs to the wallet. */
    ALREADY_MEMBER,

    /** The user already has a pending invitation for the wallet. */
    INVITATION_PENDING
}
