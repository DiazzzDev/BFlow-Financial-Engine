package Diaz.Dev.BFlow.auth.scheduler;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.scheduler.AccountHardDeletionTask;
import bflow.auth.services.ServiceAccountHardDelete;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the scheduled hard-deletion coordinator. Repository
 * filtering and the deletion transaction are covered by repository/service
 * integration tests; these tests focus on cutoff selection and per-user
 * failure isolation without starting unrelated application workers.
 */
@ExtendWith(MockitoExtension.class)
class AccountHardDeletionTaskTest {

    @Mock private RepositoryUser repositoryUser;
    @Mock private ServiceAccountHardDelete hardDeleteService;
    @InjectMocks private AccountHardDeletionTask task;

    @Test
    void run_selectsPendingDeletionUsersOlderThanThirtyDays() {
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        when(repositoryUser.findByStatusAndDeletionRequestedAtBefore(
                org.mockito.ArgumentMatchers.eq(UserStatus.PENDING_DELETION),
                cutoff.capture())).thenReturn(List.of());
        Instant before = Instant.now();

        task.run();

        Instant after = Instant.now();
        assertFalse(cutoff.getValue().isBefore(before.minus(Duration.ofDays(30))));
        assertFalse(cutoff.getValue().isAfter(after.minus(Duration.ofDays(30))));
        verify(repositoryUser).findByStatusAndDeletionRequestedAtBefore(
                UserStatus.PENDING_DELETION, cutoff.getValue());
        verifyNoInteractions(hardDeleteService);
    }

    @Test
    void run_hardDeletesUsersReturnedAsDue() {
        User due = new User();
        when(repositoryUser.findByStatusAndDeletionRequestedAtBefore(
                org.mockito.ArgumentMatchers.eq(UserStatus.PENDING_DELETION),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(due));

        task.run();

        verify(hardDeleteService).hardDelete(due);
    }

    @Test
    void run_continuesWithOtherUsersWhenOneDeletionFails() {
        User failing = new User();
        User succeeding = new User();
        when(repositoryUser.findByStatusAndDeletionRequestedAtBefore(
                org.mockito.ArgumentMatchers.eq(UserStatus.PENDING_DELETION),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(failing, succeeding));
        doThrow(new IllegalStateException("temporary failure"))
                .when(hardDeleteService).hardDelete(failing);

        task.run();

        verify(hardDeleteService).hardDelete(failing);
        verify(hardDeleteService).hardDelete(succeeding);
    }

    @Test
    void run_doesNotDeleteAnythingWhenNoPendingUserIsDue() {
        when(repositoryUser.findByStatusAndDeletionRequestedAtBefore(
                org.mockito.ArgumentMatchers.eq(UserStatus.PENDING_DELETION),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());

        task.run();

        verify(hardDeleteService, never()).hardDelete(
                org.mockito.ArgumentMatchers.any(User.class));
    }
}
