package bflow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class RateLimitService {

    /** Service for managing bucket storage. */
    private final BucketStorageService bucketStorageService;

    /**
     * Attempts to consume a token from the rate limit bucket.
     * @param key the unique key for the bucket.
     * @param policy the rate limiting policy.
     * @return a probe indicating whether the token was consumed.
     */
    public ConsumptionProbe tryConsume(
            final String key,
            final RateLimitPolicy policy
    ) {
        final Bucket bucket =
                bucketStorageService.resolveBucket(key, policy);

        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
