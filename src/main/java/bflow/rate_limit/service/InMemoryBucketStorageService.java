package bflow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.Getter;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryBucketStorageService implements BucketStorageService {
    private record Entry(Bucket bucket, long lastAccess, long ttlMillis) {}

    @Getter
    private final ConcurrentHashMap<String, Entry> storage =
            new ConcurrentHashMap<>();

    @Override
    public Bucket resolveBucket(String key, RateLimitPolicy policy) {

        long now = System.currentTimeMillis();

        Entry entry = storage.compute(key, (k, existing) -> {

            long ttl = policy.refillDuration().toMillis() * 3;

            if (existing == null) {
                return new Entry(createBucket(policy), now, ttl);
            }

            return new Entry(existing.bucket(), now, ttl);
        });

        return entry.bucket();
    }

    private Bucket createBucket(RateLimitPolicy policy) {

        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillGreedy(policy.capacity(), policy.refillDuration())
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Override
    public void cleanup() {

        long before = storage.size();

        long now = System.currentTimeMillis();

        storage.entrySet().removeIf(entry ->
                now - entry.getValue().lastAccess() > entry.getValue().ttlMillis()
        );

        long after = storage.size();

        System.out.println(
                "RateLimit cleanup: before=" + before + " after=" + after
        );
    }
}
