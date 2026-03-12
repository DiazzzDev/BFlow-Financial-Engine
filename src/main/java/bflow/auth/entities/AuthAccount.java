package bflow.auth.entities;

import bflow.auth.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a user's authentication method and credentials.
 */
@Entity
@Table(name = "auth_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthAccount {

    /** The unique identifier for the authentication account. */
    @Id
    @GeneratedValue
    private UUID id;

    /** The user associated with this authentication account. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** The provider used (e.g., LOCAL, GOOGLE). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    /** The external provider's unique user ID, if applicable. */
    @Column
    private String providerUserId;

    /** The hashed password for local authentication. */
    @Column
    private String passwordHash;

    /** Whether this user is currently active. */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** Timestamp of when the account was first created. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** The timestamp when the wallet was updated. */
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
