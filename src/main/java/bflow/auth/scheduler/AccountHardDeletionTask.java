package bflow.auth.scheduler;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.ServiceAccountHardDelete;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountHardDeletionTask {

    private static final int GRACE_PERIOD_DAYS = 30;

    private final RepositoryUser repositoryUser;
    private final ServiceAccountHardDelete hardDeleteService;

    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(GRACE_PERIOD_DAYS));

        List<User> due = repositoryUser
                .findByStatusAndDeletionRequestedAtBefore(
                        UserStatus.PENDING_DELETION, cutoff);

        for (User user : due) {
            try {
                hardDeleteService.hardDelete(user);
            } catch (Exception ex) {
                log.error(
                        "Hard delete failed for user {} — will retry "
                                + "next run",
                        user.getId(), ex
                );
            }
        }

        if (!due.isEmpty()) {
            log.info("Account hard-deletion: {} account(s) processed",
                    due.size());
        }
    }
}