package bflow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class InMemoryBucketStorageService implements BucketStorageService {
    private final ConcurrentHashMap<String, Bucket> storage = new ConcurrentHashMap<>();

    @Override
    public Bucket resolveBucket(String key, RateLimitPolicy policy) {

        return storage.computeIfAbsent(key, ignored -> createBucket(policy));
    }

    private Bucket createBucket(RateLimitPolicy policy) {

        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillGreedy(
                        policy.capacity(),
                        policy.refillDuration()
                )
                .build();

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
