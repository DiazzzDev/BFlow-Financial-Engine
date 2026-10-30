package Diaz.Dev.BFlow.subscription.services;

import bflow.subscription.entities.Plan;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.services.PlanCheckoutPresentation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCheckoutPresentationTest {

    private final PlanCheckoutPresentation presentation =
            new PlanCheckoutPresentation();

    @Test
    void proMonthlyDescriptionUsesTheLimitsStoredForThePlan() {
        Plan plan = proMonthlyPlan();

        String description = presentation.description(plan);

        assertThat(presentation.serviceName(plan))
                .isEqualTo("BFlow Pro Plan — suscripción mensual");
        assertThat(description)
                .contains("administrar tus finanzas personales")
                .contains("hasta 100 billeteras")
                .contains("hasta 100 presupuestos")
                .contains("hasta 25 transacciones recurrentes")
                .contains("hasta 100 billeteras compartidas")
                .contains("hasta 10 miembros por billetera compartida")
                .contains("crear billeteras compartidas")
                .contains("exportar tus datos")
                .contains("personalizar tu dashboard")
                .contains("cada mes");
    }

    @Test
    void yearlyPlanUsesAnnualBillingLanguage() {
        Plan plan = proMonthlyPlan();
        plan.setBillingPeriod(BillingPeriod.YEARLY);

        assertThat(presentation.serviceName(plan))
                .isEqualTo("BFlow Pro Plan — suscripción anual");
        assertThat(presentation.description(plan)).contains("cada año");
    }

    private Plan proMonthlyPlan() {
        Plan plan = new Plan();
        plan.setCode("PRO_MONTHLY");
        plan.setName("Pro Plan");
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        plan.setMaxWallets(100);
        plan.setMaxBudgets(100);
        plan.setMaxRecurringTransactions(25);
        plan.setMaxSharedWallets(100);
        plan.setMaxWalletMembers(10);
        return plan;
    }
}
