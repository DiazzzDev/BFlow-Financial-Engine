package bflow.common.idempotency.scheduler;

import bflow.common.idempotency.repository.RepositoryIdempotency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupTask {

    /**
    * Repository used to remove expired idempotency records.
    */
    private final RepositoryIdempotency repositoryIdempotency;

    /**
    * Removes expired idempotency records from the persistence store.
    * Runs automatically every hour according to the configured cron expression.
    */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredKeys() {
        int deleted = repositoryIdempotency.deleteExpired(Instant.now());
        log.info("Idempotency cleanup: {} expired keys removed", deleted);
    }
}
