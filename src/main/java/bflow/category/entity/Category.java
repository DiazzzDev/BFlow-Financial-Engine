package bflow.category.entity;

import bflow.category.enums.CategoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GenerationType;
import jakarta.persistence.EnumType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {

    /**
     * The unique identifier for this category.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The name of this category.
     */
    @Column(nullable = false)
    private String name;

    /**
     * The type of this category (INCOME, EXPENSE, or TRANSFER).
     */
    @Enumerated(EnumType.STRING)
    private CategoryType type;

    /**
     * Flag indicating if this category is system-defined.
     */
    @Column(nullable = false)
    private boolean systemDefined;

    /**
     * The icon identifier for this category.
     */
    private String icon;

    /**
     * The color code for this category.
     */
    private String color;

    /**
     * Timestamp when this category was created.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

}
