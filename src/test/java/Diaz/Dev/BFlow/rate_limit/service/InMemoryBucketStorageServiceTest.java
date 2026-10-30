package Diaz.Dev.BFlow.rate_limit.service;

import bflow.rate_limit.policy.RateLimitPolicy;
import bflow.rate_limit.service.InMemoryBucketStorageService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryBucketStorageServiceTest {

    @Test
    void keepsIndependentCapacityPerKeyAndReusesEachBucket() {
        InMemoryBucketStorageService storage =
                new InMemoryBucketStorageService();
        RateLimitPolicy policy = new RateLimitPolicy(2, Duration.ofMinutes(1));

        Bucket firstUser = storage.resolveBucket("user:one", policy);

        assertTrue(firstUser.tryConsume(1));
        assertTrue(firstUser.tryConsume(1));
        assertFalse(firstUser.tryConsume(1));
        assertSame(firstUser, storage.resolveBucket("user:one", policy));

        Bucket secondUser = storage.resolveBucket("user:two", policy);

        assertNotSame(firstUser, secondUser);
        assertTrue(secondUser.tryConsume(1));
        assertTrue(secondUser.tryConsume(1));
        assertFalse(secondUser.tryConsume(1));
        assertEquals(2, storage.getStorage().size());
    }

    @Test
    void cleanupKeepsRecentlyUsedBuckets() {
        InMemoryBucketStorageService storage =
                new InMemoryBucketStorageService();
        RateLimitPolicy policy = new RateLimitPolicy(2, Duration.ofDays(1));
        Bucket bucket = storage.resolveBucket("user:one", policy);

        storage.cleanup();

        assertSame(bucket, storage.resolveBucket("user:one", policy));
        assertEquals(1, storage.getStorage().size());
    }

    @Test
    void cleanupRemovesExpiredBucketAndAllowsFreshCapacity()
            throws InterruptedException {
        InMemoryBucketStorageService storage =
                new InMemoryBucketStorageService();
        RateLimitPolicy policy = new RateLimitPolicy(1, Duration.ofMillis(1));
        Bucket expired = storage.resolveBucket("user:one", policy);
        assertTrue(expired.tryConsume(1));
        Thread.sleep(50);

        storage.cleanup();

        assertEquals(0, storage.getStorage().size());
        Bucket renewed = storage.resolveBucket("user:one", policy);
        assertNotSame(expired, renewed);
        assertTrue(renewed.tryConsume(1));
    }
}
