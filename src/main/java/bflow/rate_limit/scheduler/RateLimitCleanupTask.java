package bflow.rate_limit.scheduler;

import bflow.rate_limit.service.BucketStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitCleanupTask {

    /** Initial delay in minutes for cleanup task. */
    private static final long INITIAL_DELAY_MINUTES = 5;
    /** Delay in seconds for cleanup task. */
    private static final long DELAY_SECONDS = 60;
    /** Conversion factor from seconds to milliseconds. */
    private static final long SECONDS_TO_MILLIS = 1000;

    /** Service for managing bucket storage. */
    private final BucketStorageService storageService;

    /**
     * Scheduled cleanup task for removing expired rate limit buckets.
     */
    @Scheduled(fixedRate = INITIAL_DELAY_MINUTES * DELAY_SECONDS
            * SECONDS_TO_MILLIS)
    public void cleanup() {
        storageService.cleanup();
    }
}
