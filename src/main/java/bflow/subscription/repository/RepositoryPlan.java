package bflow.subscription.repository;

import bflow.subscription.entities.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
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
     * Returns the plans that can currently be offered to customers.
     *
     * @return active plans in a stable, price-first order
     */
    List<Plan> findAllByActiveTrueOrderByPriceAsc();

    /**
     * Finds an active plan by its identifier.
     *
     * @param id plan identifier
     * @return the active plan, if it exists
     */
    Optional<Plan> findByIdAndActiveTrue(UUID id);

}

