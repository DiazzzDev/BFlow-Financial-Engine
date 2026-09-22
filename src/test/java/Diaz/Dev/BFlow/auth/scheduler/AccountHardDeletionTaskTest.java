package Diaz.Dev.BFlow.auth.scheduler;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.scheduler.AccountHardDeletionTask;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Exercises {@link AccountHardDeletionTask#run()} directly — the
 * date-filtering query and the fact that each user is processed in
 * its own transaction (see the REQUIRES_NEW discussion from the
 * transaction bug fix). Does not use CognitoAdminService with real
 * AWS calls: fixtures leave cognitoSub null, and
 * CognitoAdminService.deleteUser() no-ops on a null/blank sub.
 */
@SpringBootTest(classes = bflow.BFlowApplication.class)
@ActiveProfiles("test")
@Transactional
class AccountHardDeletionTaskTest {

    @Autowired private RepositoryUser repositoryUser;
    @Autowired private AccountHardDeletionTask task;

    @Test
    void run_hardDeletes_dueUsers() {
        User due = persistUser(
                UserStatus.PENDING_DELETION,
                Instant.now().minus(Duration.ofDays(31))
        );

        task.run();

        assertTrue(repositoryUser.findById(due.getId()).isEmpty());
    }

    @Test
    void run_leaves_notYetDueUsers_untouched() {
        User notDue = persistUser(
                UserStatus.PENDING_DELETION,
                Instant.now().minus(Duration.ofDays(5))
        );

        task.run();

        assertFalse(repositoryUser.findById(notDue.getId()).isEmpty());
    }

    @Test
    void run_ignores_activeUsers_regardlessOfDate() {
        User active = persistUser(
                UserStatus.ACTIVE,
                Instant.now().minus(Duration.ofDays(60))
        );

        task.run();

        assertFalse(repositoryUser.findById(active.getId()).isEmpty());
    }

    private User persistUser(
            final UserStatus status, final Instant deletionRequestedAt
    ) {
        User user = new User();
        user.setEmail("run-test-" + java.util.UUID.randomUUID() + "@example.com");
        user.setName("Run Test User");
        user.setStatus(status);
        user.setDeletionRequestedAt(deletionRequestedAt);
        user.setEmailVerified(true);
        return repositoryUser.save(user);
    }
}