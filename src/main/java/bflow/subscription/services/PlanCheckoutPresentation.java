package bflow.subscription.services;

import bflow.subscription.entities.Plan;
import bflow.subscription.enums.BillingPeriod;
import org.springframework.stereotype.Component;

/** Builds customer-facing product copy for a Wompi checkout. */
@Component
public class PlanCheckoutPresentation {

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
     * Returns a natural-language description based on the plan limits.
     *
     * @param plan plan selected by the customer
     * @return customer-facing description for Wompi
     */
    public String description(final Plan plan) {
        String period = plan.getBillingPeriod() == BillingPeriod.MONTHLY
                ? "cada mes" : "cada año";
        return "Este plan te permite administrar tus finanzas personales "
                + "con mayor capacidad. Incluye hasta %d billeteras, hasta "
                + "%d presupuestos, hasta %d transacciones recurrentes, "
                + "hasta %d billeteras compartidas y hasta %d miembros por "
                + "billetera compartida. También permite crear billeteras "
                + "compartidas, exportar tus datos y personalizar tu dashboard. "
                + "El cobro se realiza %s."
                .formatted(
                        plan.getMaxWallets(),
                        plan.getMaxBudgets(),
                        plan.getMaxRecurringTransactions(),
                        plan.getMaxSharedWallets(),
                        plan.getMaxWalletMembers(),
                        period
                );
    }
}
