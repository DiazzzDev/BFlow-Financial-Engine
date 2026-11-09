package bflow.gmail.scheduler;

import bflow.gmail.repository.GmailOAuthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailOAuthStateCleanupTask {
    private final GmailOAuthStateRepository repository;

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void removeExpiredStates() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }
}
