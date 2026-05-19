package bflow.rate_limit.policy;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public final class RateLimitPolicyRegistry {
    /** Map of policy keys to their corresponding rate limit policies. */
    private final Map<String, RateLimitPolicy> policies = Map.of(

            "LOGIN",
            new RateLimitPolicy(5, Duration.ofMinutes(5)),

            "REGISTER",
            new RateLimitPolicy(3, Duration.ofMinutes(10)),

            "FORGOT_PASSWORD",
            new RateLimitPolicy(3, Duration.ofMinutes(15)),

            "RESEND_VERIFICATION",
            new RateLimitPolicy(3, Duration.ofMinutes(10)),

            "VERIFY_EMAIL",
            new RateLimitPolicy(10, Duration.ofMinutes(5)),

            "AUTHENTICATED_API",
            new RateLimitPolicy(100, Duration.ofMinutes(3))
    );

    /**
     * Retrieves the rate limiting policy for the given key.
     * @param key the policy key.
     * @return the rate limiting policy, or null if not found.
     */
    public RateLimitPolicy getPolicy(final String key) {
        return policies.get(key);
    }
}
