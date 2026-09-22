package bflow.auth.enums;

/**
 * Enumeration representing the current state of a user account.
 */
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