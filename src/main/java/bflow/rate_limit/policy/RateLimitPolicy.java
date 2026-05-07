package bflow.rate_limit.policy;

import java.time.Duration;

public record RateLimitPolicy(
        long capacity,
        Duration refillDuration
) {
}
