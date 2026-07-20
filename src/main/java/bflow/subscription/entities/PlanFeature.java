package bflow.subscription.entities;

import jakarta.persistence.*;
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

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feature_id")
    private Feature feature;

    /**
     * null = ilimitado
     */
    private Integer limit;

    @Column(nullable = false)
    private boolean enabled = true;
}
