package bflow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public final class InMemoryBucketStorageService
        implements BucketStorageService {
    /** Time-to-live multiplier for bucket entries. */
    private static final long TTL_MULTIPLIER = 3;

    /**
     * Internal record for storing bucket entries with metadata.
     * @param bucket the rate limit bucket.
     * @param lastAccess the last access timestamp in milliseconds.
     * @param ttlMillis the time-to-live duration in milliseconds.
     */
    private record Entry(Bucket bucket, long lastAccess,
            long ttlMillis) { }

    /** Storage for bucket entries indexed by key. */
    @Getter
    private final ConcurrentHashMap<String, Entry> storage =
            new ConcurrentHashMap<>();

    /**
     * Resolves or creates a bucket for the given key and policy.
     * @param key the unique key for the bucket.
     * @param policy the rate limiting policy.
     * @return the resolved bucket.
     */
    @Override
    public Bucket resolveBucket(final String key,
            final RateLimitPolicy policy) {

        long now = System.currentTimeMillis();

        Entry entry = storage.compute(key, (k, existing) -> {

            long ttl = policy.refillDuration().toMillis() * TTL_MULTIPLIER;

            if (existing == null) {
                return new Entry(createBucket(policy), now, ttl);
            }

            return new Entry(existing.bucket(), now, ttl);
        });

        return entry.bucket();
    }

    /**
     * Creates a new bucket with the specified policy.
     * @param policy the rate limiting policy.
     * @return a new bucket configured with the policy.
     */
    private Bucket createBucket(final RateLimitPolicy policy) {

        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillGreedy(policy.capacity(), policy.refillDuration())
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Cleans up expired bucket entries from storage.
     */
    @Override
    public void cleanup() {

        long before = storage.size();

        long now = System.currentTimeMillis();

        storage.entrySet().removeIf(entry -> {
            long entryTtl = entry.getValue().ttlMillis();
            long entryLastAccess = entry.getValue().lastAccess();
            return now - entryLastAccess > entryTtl;
        });

        long after = storage.size();
        long removed = before - after;

        if (removed > 0 && log.isDebugEnabled()) {
            log.debug("RateLimit cleanup: removed={}, remaining={}",
                    removed, after);
        }
    }
}

