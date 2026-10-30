package Diaz.Dev.BFlow.subscription.services;

import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Feature;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.repository.RepositoryPlanFeature;
import bflow.subscription.services.PlanCheckoutPresentation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PlanCheckoutPresentationTest {

    @Mock private RepositoryPlanFeature repositoryPlanFeature;

    private PlanCheckoutPresentation presentation;

    @BeforeEach
    void setUp() {
        presentation = new PlanCheckoutPresentation(repositoryPlanFeature);
    }

    @Test
    void proMonthlyDescriptionUsesTheLimitsStoredForThePlan() {
        Plan plan = proMonthlyPlan();
        when(repositoryPlanFeature.findByPlanId(plan.getId()))
                .thenReturn(proMonthlyFeatures());

        String description = presentation.description(plan);

        assertThat(presentation.serviceName(plan))
                .isEqualTo("BFlow Pro Plan — suscripción mensual");
        assertThat(description)
                .contains("Administra tus finanzas personales")
                .contains("hasta 100 billeteras")
                .contains("hasta 100 presupuestos")
                .contains("hasta 25 transacciones recurrentes")
                .contains("hasta 100 billeteras compartidas")
                .contains("hasta 10 miembros por billetera compartida")
                .contains("crear billeteras compartidas")
                .contains("exportar tus datos")
                .contains("personalizar tu panel financiero")
                .contains("cada mes");
    }

    @Test
    void yearlyPlanUsesAnnualBillingLanguage() {
        Plan plan = proMonthlyPlan();
        plan.setBillingPeriod(BillingPeriod.YEARLY);
        when(repositoryPlanFeature.findByPlanId(plan.getId()))
                .thenReturn(List.of());

        assertThat(presentation.serviceName(plan))
                .isEqualTo("BFlow Pro Plan — suscripción anual");
        assertThat(presentation.description(plan)).contains("cada año");
    }

    private Plan proMonthlyPlan() {
        Plan plan = new Plan();
        plan.setCode("PRO_MONTHLY");
        plan.setName("Pro Plan");
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        plan.setId(java.util.UUID.randomUUID());
        return plan;
    }

    private List<PlanFeature> proMonthlyFeatures() {
        return List.of(
                feature("WALLETS", 100, true),
                feature("BUDGETS", 100, true),
                feature("RECURRING_TRANSACTIONS", 25, true),
                feature("SHARED_WALLETS", 100, true),
                feature("WALLET_MEMBERS", 10, true),
                feature("CAN_CREATE_SHARED_WALLETS", null, true),
                feature("EXPORT", null, true),
                feature("DASHBOARD_CUSTOMIZATION", null, true)
        );
    }

    private PlanFeature feature(String code, Integer limit, boolean enabled) {
        Feature feature = new Feature();
        feature.setCode(code);
        PlanFeature planFeature = new PlanFeature();
        planFeature.setFeature(feature);
        planFeature.setLimit(limit);
        planFeature.setEnabled(enabled);
        return planFeature;
    }
}
