package bflow.subscription.services;

import bflow.common.exception.NotFoundException;
import bflow.subscription.entities.Plan;
import bflow.subscription.repository.RepositoryPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

        /** Code for the free plan. */
        private static final String FREE_PLAN_CODE = "FREE";
        /** Repository for plan lookup and persistence. */
        private final RepositoryPlan repositoryPlan;

        /**
         * Retrieve the configured free plan.
         *
         * @return the free Plan entity
         */
        public Plan getFreePlan() {

        return repositoryPlan
                .findByCode(FREE_PLAN_CODE)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Free plan not found"
                        ));
    }

    /**
     * Retrieve the plan identified by the provided plan code.
     *
     * @param code plan code
     * @return the matching Plan entity
     */
    public Plan getByCode(final String code) {

        return repositoryPlan
                .findByCode(code)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Plan not found"
                        ));
    }

}
