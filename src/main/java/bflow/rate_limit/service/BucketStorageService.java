package bflow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import io.github.bucket4j.Bucket;

public interface BucketStorageService {
    Bucket resolveBucket(String key, RateLimitPolicy policy);
}
