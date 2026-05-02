package bflow.merchant;

import bflow.category.entity.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Merchant pattern entity for category detection.
 */
@Entity
@Table(name = "merchant_patterns")
@Getter
@Setter
public final class MerchantPattern {

    /**
     * The merchant pattern ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Normalized text pattern to search for in descriptions.
     */
    @Column(nullable = false)
    private String pattern;

    /**
     * The category associated with this pattern.
     */
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * The priority for matching (higher = more priority).
     */
    @Column(nullable = false)
    private Integer priority = 0;
}
