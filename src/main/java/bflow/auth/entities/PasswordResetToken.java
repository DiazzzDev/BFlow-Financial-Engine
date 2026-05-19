package bflow.auth.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a password reset token.
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_password_reset_user", columnList = "user_id"),
        @Index(name = "idx_password_reset_hash", columnList = "token_hash")
})
@Getter
@Setter
public class PasswordResetToken {

    /** Unique identifier for the reset token. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user ID associated with this reset token. */
    @Column(nullable = false)
    private UUID userId;

    /** The hashed token value. */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    /** The expiration date and time of the token. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Whether the token has been used. */
    @Column(nullable = false)
    private boolean used = false;

    /** The timestamp when the token was created. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
