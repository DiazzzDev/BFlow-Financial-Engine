package bflow.subscription.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "features")
@Getter
@Setter
public class Feature {

    /** Maximum length for the feature code column. */
    private static final int MAX_CODE_LENGTH = 100;

    /** Maximum length for the feature name column. */
    private static final int MAX_NAME_LENGTH = 150;

    /** Unique identifier for the feature. */
    @Id
    @GeneratedValue
    private UUID id;

    /** Stable code used to identify the feature. */
    @Column(nullable = false, unique = true, length = MAX_CODE_LENGTH)
    private String code;

    /** Human-readable feature name. */
    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    /** Whether the feature supports quantity-based limits. */
    @Column(nullable = false)
    private boolean limitable;
}

