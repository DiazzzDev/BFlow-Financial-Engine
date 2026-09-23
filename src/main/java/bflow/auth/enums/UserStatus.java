package bflow.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeration representing the current state of a user account.
 */
@Schema(description = "User account lifecycle state: ACTIVE allows access; "
        + "SUSPENDED restricts access; PENDING_DELETION is recoverable; "
        + "DELETED is removed.", allowableValues = {"ACTIVE", "SUSPENDED",
        "PENDING_DELETION", "DELETED"})
public enum UserStatus {
    /** Account is active and can log in. */
    ACTIVE,

    /** Account is temporarily restricted. */
    SUSPENDED,

    /** Account is scheduled for deletion; still recoverable. */
    PENDING_DELETION,

    /** Account has been marked for deletion. */
    DELETED
}
