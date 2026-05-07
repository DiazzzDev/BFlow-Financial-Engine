package bflow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import io.github.bucket4j.Bucket;

public interface BucketStorageService {
    /**
     * Resolves or creates a bucket for the given key and policy.
     * @param key the unique key for the bucket.
     * @param policy the rate limiting policy for the bucket.
     * @return the bucket for rate limiting.
     */
    Bucket resolveBucket(String key, RateLimitPolicy policy);

    /**
     * Cleans up expired buckets from storage.
     */
    void cleanup();
}
