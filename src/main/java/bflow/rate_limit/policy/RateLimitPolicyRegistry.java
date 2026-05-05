package bflow.rate_limit.policy;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class RateLimitPolicyRegistry {
    private final Map<String, RateLimitPolicy> policies = Map.of(

            "LOGIN",
            new RateLimitPolicy(5, Duration.ofMinutes(1)),

            "REGISTER",
            new RateLimitPolicy(3, Duration.ofMinutes(10)),

            "FORGOT_PASSWORD",
            new RateLimitPolicy(3, Duration.ofMinutes(15)),

            "AUTHENTICATED_API",
            new RateLimitPolicy(100, Duration.ofMinutes(1))
    );

    public RateLimitPolicy getPolicy(String key) {
        return policies.get(key);
    }
}
