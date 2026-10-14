package bflow.subscription.dto;

import bflow.subscription.enums.BillingPeriod;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public plan catalogue entry, including every configured benefit.
 *
 * @param id plan identifier used by checkout
 * @param code stable product code
 * @param name display name
 * @param price charge in the application's configured currency
 * @param billingPeriod billing cadence
 * @param benefits all configured feature entitlements
 */
public record PlanResponse(
        UUID id,
        String code,
        String name,
        BigDecimal price,
        BillingPeriod billingPeriod,
        List<PlanBenefitResponse> benefits
) {
}
