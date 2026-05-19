package bflow.auth.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "email_verification_tokens",
        indexes = {
                @Index(
                        name = "idx_email_verification_user",
                        columnList = "userId"
                ),
                @Index(
                        name = "idx_email_verification_hash",
                        columnList = "tokenHash"
                )
        }
)
@Getter
@Setter
public class EmailVerificationToken {
    /** The unique identifier for the verification token. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The user associated with this verification token. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The hash of the verification token for secure storage. */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    /** The date and time when this token expires. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** The date and time when this token was created. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
