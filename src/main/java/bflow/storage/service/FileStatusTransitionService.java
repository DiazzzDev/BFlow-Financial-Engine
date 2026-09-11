package bflow.storage.service;

import bflow.common.i18n.MessageService;
import bflow.storage.entity.StoredFile;
import bflow.storage.enums.FileStatus;
import bflow.storage.repository.RepositoryStoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists a {@link StoredFile} status transition in its own,
 * independent transaction.
 *
 * <p>Kept as a separate bean (rather than a private method on
 * {@link FileUploadService}) so the {@code REQUIRES_NEW} propagation
 * actually applies: Spring's transactional proxy only intercepts
 * calls that go through the bean, not self-invocations within the
 * same class. This mirrors the isolation pattern already used by
 * {@code RecurringTransactionExecutor} for per-item failure
 * tracking.</p>
 */
@Service
@RequiredArgsConstructor
public class FileStatusTransitionService {

    /** Repository for stored file records. */
    private final RepositoryStoredFile repositoryStoredFile;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Transitions a stored file to a new status and commits
     * immediately, independent of the caller's transaction.
     *
     * @param fileId the stored file identifier
     * @param status the status to transition to
     * @return the updated stored file
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredFile transition(final UUID fileId, final FileStatus status) {

        StoredFile file = repositoryStoredFile.findById(fileId)
                .orElseThrow(() -> new IllegalStateException(
                        messageService.get(
                                "file.notFoundInternal", fileId
                        )));

        file.setStatus(status);

        return repositoryStoredFile.save(file);
    }
}
