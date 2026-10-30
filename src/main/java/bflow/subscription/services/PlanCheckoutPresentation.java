package bflow.subscription.services;

import bflow.subscription.entities.Plan;
import bflow.subscription.entities.PlanFeature;
import bflow.subscription.enums.BillingPeriod;
import bflow.subscription.repository.RepositoryPlanFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds customer-facing product copy for a Wompi checkout. */
@Component
@RequiredArgsConstructor
public class PlanCheckoutPresentation {

    private final RepositoryPlanFeature repositoryPlanFeature;

    private static final Map<String, String> BENEFIT_LABELS = Map.of(
            "WALLETS", "billeteras para organizar tus finanzas",
            "BUDGETS", "presupuestos para controlar tus gastos",
            "RECURRING_TRANSACTIONS", "transacciones recurrentes",
            "SHARED_WALLETS", "billeteras compartidas",
            "WALLET_MEMBERS", "miembros por billetera compartida"
    );

    /**
     * Returns the service name displayed by Wompi.
     *
     * @param plan plan selected by the customer
     * @return checkout service name
     */
    public String serviceName(final Plan plan) {
        String period = plan.getBillingPeriod() == BillingPeriod.MONTHLY
                ? "mensual" : "anual";
        return "BFlow " + plan.getName() + " — suscripción " + period;
    }

    /**
     * Returns a natural-language description based on configured plan features.
     *
     * @param plan plan selected by the customer
     * @return customer-facing description for Wompi
     */
    public String description(final Plan plan) {
        String period = plan.getBillingPeriod() == BillingPeriod.MONTHLY
                ? "cada mes" : "cada año";
        List<PlanFeature> features = repositoryPlanFeature
                .findByPlanId(plan.getId());
        List<String> benefits = features.stream()
                .filter(PlanFeature::isEnabled)
                .map(this::describeFeature)
                .filter(value -> value != null)
                .collect(Collectors.toList());
        return "Administra tus finanzas personales con herramientas para "
                + "organizar y controlar tu dinero. "
                + (benefits.isEmpty() ? "" : "Incluye: "
                + String.join("; ", benefits) + ". ")
                + "El cobro se realiza " + period + ".";
    }

    private String describeFeature(final PlanFeature planFeature) {
        String code = planFeature.getFeature().getCode();
        String label = BENEFIT_LABELS.get(code);
        if (label != null) {
            Integer limit = planFeature.getLimit();
            return limit == null ? label + " sin límite configurado"
                    : "hasta " + limit + " " + label;
        }
        return switch (code) {
            case "CAN_CREATE_SHARED_WALLETS" ->
                    "crear billeteras compartidas";
            case "EXPORT" -> "exportar tus datos";
            case "DASHBOARD_CUSTOMIZATION" ->
                    "personalizar tu panel financiero";
            default -> null;
        };
    }
}
