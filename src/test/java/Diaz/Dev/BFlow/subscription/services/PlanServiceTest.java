package Diaz.Dev.BFlow.subscription.services;

import bflow.subscription.dto.PlanResponse;
import bflow.subscription.entities.Feature;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.repository.RepositoryPlan;
import bflow.subscription.repository.RepositoryPlanFeature;
import bflow.subscription.services.PlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock private RepositoryPlan repositoryPlan;
    @Mock private RepositoryPlanFeature repositoryPlanFeature;
    @InjectMocks private PlanService planService;

    @Test
    void getActivePlansIncludesEveryConfiguredBenefit() {
        UUID planId = UUID.randomUUID();
        Plan plan = new Plan();
        plan.setId(planId);
        plan.setCode("PRO_MONTHLY");
        plan.setName("Pro Plan");
        plan.setPrice(new BigDecimal("9.99"));
        plan.setBillingPeriod(BillingPeriod.MONTHLY);

        PlanFeature export = benefit("EXPORT", "Exportar datos", false,
                null, true);
        PlanFeature budgets = benefit("BUDGETS", "Presupuestos", true,
                100, true);

        when(repositoryPlan.findAllByActiveTrueOrderByPriceAsc())
                .thenReturn(List.of(plan));
        when(repositoryPlanFeature.findByPlanId(planId))
                .thenReturn(List.of(export, budgets));

        List<PlanResponse> result = planService.getActivePlans();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().benefits())
                .extracting(benefit -> benefit.code())
                .containsExactly("BUDGETS", "EXPORT");
        assertThat(result.getFirst().benefits().getFirst().limit()).isEqualTo(100);
    }

    private PlanFeature benefit(
            final String code,
            final String name,
            final boolean limitable,
            final Integer limit,
            final boolean enabled
    ) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setName(name);
        feature.setLimitable(limitable);

        PlanFeature planFeature = new PlanFeature();
        planFeature.setFeature(feature);
        planFeature.setLimit(limit);
        planFeature.setEnabled(enabled);
        return planFeature;
    }
}
