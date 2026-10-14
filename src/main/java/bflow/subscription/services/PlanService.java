package bflow.subscription.services;

import bflow.common.exception.NotFoundException;
import bflow.subscription.entities.Plan;
import bflow.subscription.dto.PlanBenefitResponse;
import bflow.subscription.dto.PlanResponse;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.repository.RepositoryPlan;
import bflow.subscription.repository.RepositoryPlanFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

        /** Code for the free plan. */
        private static final String FREE_PLAN_CODE = "FREE";
        /** Repository for plan lookup and persistence. */
        private final RepositoryPlan repositoryPlan;

        /** Repository for the benefits assigned to each plan. */
        private final RepositoryPlanFeature repositoryPlanFeature;

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
     * Lists every active plan together with all of its benefits.
     *
     * @return public plan catalogue
     */
    public List<PlanResponse> getActivePlans() {
        return repositoryPlan.findAllByActiveTrueOrderByPriceAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Finds one active plan together with all of its benefits.
     *
     * @param planId plan identifier
     * @return public plan information
     */
    public PlanResponse getActivePlan(final UUID planId) {
        Plan plan = repositoryPlan.findByIdAndActiveTrue(planId)
                .orElseThrow(() -> new NotFoundException("Plan not found"));
        return toResponse(plan);
    }

    private PlanResponse toResponse(final Plan plan) {
        List<PlanBenefitResponse> benefits = repositoryPlanFeature
                .findByPlanId(plan.getId())
                .stream()
                .sorted(Comparator.comparing(feature -> feature.getFeature().getCode()))
                .map(this::toBenefitResponse)
                .toList();

        return new PlanResponse(
                plan.getId(), plan.getCode(), plan.getName(), plan.getPrice(),
                plan.getBillingPeriod(), benefits
        );
    }

    private PlanBenefitResponse toBenefitResponse(final PlanFeature planFeature) {
        return new PlanBenefitResponse(
                planFeature.getFeature().getCode(),
                planFeature.getFeature().getName(),
                planFeature.getFeature().isLimitable(),
                planFeature.getLimit(),
                planFeature.isEnabled()
        );
    }
}
