package bflow.subscription.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "plan_features",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {
                        "plan_id",
                        "feature_id"
                }
        )
)
@Getter
@Setter
public class PlanFeature {

    /** Unique identifier for the plan-feature relation. */
    @Id
    @GeneratedValue
    private UUID id;

    /** Plan that owns this feature assignment. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    /** Feature attached to the plan. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "feature_id")
    private Feature feature;

    /** Maximum allowed quantity for the feature. Null means unlimited. */
    private Integer limit;

    /** Whether the feature is enabled for the plan. */
    @Column(nullable = false)
    private boolean enabled = true;
}

