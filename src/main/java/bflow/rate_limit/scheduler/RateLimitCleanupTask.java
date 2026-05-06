package bflow.rate_limit.scheduler;

import bflow.rate_limit.service.BucketStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitCleanupTask {

    private final BucketStorageService storageService;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanup() {
        storageService.cleanup();
    }
}