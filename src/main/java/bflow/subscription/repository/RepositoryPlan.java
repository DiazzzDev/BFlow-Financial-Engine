package bflow.subscription.repository;

import bflow.subscription.entities.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryPlan extends JpaRepository<Plan, UUID> {

    /**
     * Find a plan by its unique code.
     *
     * @param code the plan code
     * @return optional plan matching the code
     */
    Optional<Plan> findByCode(String code);

    /**
     * Find a plan by its provider link identifier.
     *
     * @param providerLinkId the Wompi provider link identifier
     * @return optional plan matching the identifier
     */
    Optional<Plan> findByProviderLinkId(String providerLinkId);
}

