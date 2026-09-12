package bflow.auth.entities;

import bflow.auth.enums.NameSource;
import bflow.auth.enums.PictureSource;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.enums.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a system user.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** Unique identifier for the user. */
    @Id
    @GeneratedValue
    private UUID id;

    /** Unique email address of the user. */
    @Column(nullable = false, unique = true)
    private String email;

    /** Display name of the user, captured from the Cognito ID token. */
    @Column
    private String name;

    /** Origin of the current display name. */
    @Enumerated(EnumType.STRING)
    @Column(name = "name_source", nullable = false)
    @Builder.Default
    private NameSource nameSource = NameSource.GOOGLE;

    /** URL of the user's profile picture. */
    @Column(name = "picture_url")
    private String pictureUrl;

    /** Origin of the current profile picture. */
    @Enumerated(EnumType.STRING)
    @Column(name = "picture_source", nullable = false)
    @Builder.Default
    private PictureSource pictureSource = PictureSource.NONE;

    /** The user's explicit language preference for messages and emails. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SupportedLanguage language = SupportedLanguage.ES;

    /** The set of roles assigned to the user. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    @Builder.Default
    private Set<String> roles = Set.of("ROLE_USER");

    /** Indicates whether the user email is verified. */
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerified = false;

    /** Unique Cognito subject identifier associated with the user. */
    @Column(unique = true)
    private String cognitoSub;

    /** Indicates whether the user account is active. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /** Timestamp when the user account was created. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp when the user account was last updated. */
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
