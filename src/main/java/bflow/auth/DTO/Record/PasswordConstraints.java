package bflow.auth.DTO.Record;

/**
 * Constants for password validation constraints.
 */
public final class PasswordConstraints {
    /** Minimum password length. */
    public static final int MIN_PASSWORD_LENGTH = 12;

    /** Maximum password length. */
    public static final int MAX_PASSWORD_LENGTH = 255;

    /** Private constructor to prevent instantiation. */
    private PasswordConstraints() {
        throw new UnsupportedOperationException(
                "Utility class should not be instantiated"
        );
    }
}
