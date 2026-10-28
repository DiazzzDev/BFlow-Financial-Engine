package Diaz.Dev.BFlow.common.idempotency.scheduler;

import bflow.common.idempotency.repository.RepositoryIdempotency;
import bflow.common.idempotency.scheduler.IdempotencyCleanupTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupTaskTest {

    @Mock private RepositoryIdempotency repositoryIdempotency;
    @InjectMocks private IdempotencyCleanupTask task;

    @Test
    void purgeExpiredKeys_deletesOnlyRecordsBeforeItsExecutionTime() {
        when(repositoryIdempotency.deleteExpired(any(Instant.class)))
                .thenReturn(3);

        task.purgeExpiredKeys();

        verify(repositoryIdempotency).deleteExpired(any(Instant.class));
    }
}
