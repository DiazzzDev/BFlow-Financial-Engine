package bflow.auth;

import java.util.UUID;

/** Well-known identifiers for system/placeholder user accounts. */
public final class SystemUsers {

    /**
     * Placeholder user that inherits contributions and billing
     * records from hard-deleted accounts, so shared-wallet history
     * and payment records survive without keeping the original
     * user's PII. Displayed to other wallet members as "Usuario de
     * Bflow".
     */
    public static final UUID GHOST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SystemUsers() {
    }
}