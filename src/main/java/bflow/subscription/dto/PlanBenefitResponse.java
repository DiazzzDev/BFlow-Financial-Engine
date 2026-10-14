package bflow.subscription.dto;

/**
 * A benefit assigned to a subscription plan.
 *
 * @param code stable feature code for programmatic frontend use
 * @param name human-readable feature name
 * @param limitable whether the benefit accepts a numeric limit
 * @param limit maximum quantity; null means the enabled benefit is unlimited
 * @param enabled whether the benefit is included in the plan
 */
public record PlanBenefitResponse(
        String code,
        String name,
        boolean limitable,
        Integer limit,
        boolean enabled
) {
}
