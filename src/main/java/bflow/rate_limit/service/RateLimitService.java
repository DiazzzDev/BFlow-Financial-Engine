package bflow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final BucketStorageService bucketStorageService;

    public ConsumptionProbe tryConsume(
            String key,
            RateLimitPolicy policy
    ) {
        Bucket bucket =
                bucketStorageService.resolveBucket(key, policy);

        return bucket.tryConsumeAndReturnRemaining(1);
    }
}