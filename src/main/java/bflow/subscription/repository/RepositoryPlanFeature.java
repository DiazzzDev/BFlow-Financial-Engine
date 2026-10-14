package bflow.subscription.repository;

import bflow.subscription.entities.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryPlanFeature
    extends JpaRepository<PlanFeature, UUID> {

    /**
     * Finds a plan feature by plan ID and feature code.
     *
     * @param planId the plan UUID
     * @param featureCode the feature code
     * @return an optional containing the matching plan feature if found
     */
    Optional<PlanFeature> findByPlanIdAndFeatureCode(
        UUID planId, String featureCode
    );

    /**
     * Retrieves all features associated with the specified plan.
     *
     * @param planId the plan UUID
     * @return a list of plan features
     */
    @EntityGraph(attributePaths = "feature")
    List<PlanFeature> findByPlanId(UUID planId);
}
