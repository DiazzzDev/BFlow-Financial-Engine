package Diaz.Dev.BFlow.gmail;

import bflow.gmail.entity.GmailImport;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.service.GmailImportStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GmailImportStateServiceTest {
    private GmailImportRepository repository;
    private GmailImportStateService service;
    private UUID importId;

    @BeforeEach
    void setUp() {
        repository = mock(GmailImportRepository.class);
        service = new GmailImportStateService(repository);
        importId = UUID.randomUUID();
    }

    @Test
    void claimUsesRepositoryAtomicClaimResult() {
        when(repository.claimQueued(org.mockito.ArgumentMatchers.eq(importId),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1, 0);

        assertThat(service.claim(importId)).isTrue();
        assertThat(service.claim(importId)).isFalse();
    }

    @Test
    void markProcessingUpdatesOnlyExistingImportAndClearsOldError() {
        GmailImport item = new GmailImport();
        item.setErrorCode("OLD_ERROR");
        when(repository.findById(importId)).thenReturn(Optional.of(item));

        service.markProcessing(importId, "tmp/gmail/object", "job-123");

        assertThat(item.getStatus()).isEqualTo("PROCESSING");
        assertThat(item.getS3ObjectKey()).isEqualTo("tmp/gmail/object");
        assertThat(item.getTextractJobId()).isEqualTo("job-123");
        assertThat(item.getErrorCode()).isNull();
        verify(repository).save(item);
    }

    @Test
    void markFailedPersistsFailureCodeAndIgnoresMissingImport() {
        GmailImport item = new GmailImport();
        when(repository.findById(importId)).thenReturn(Optional.of(item), Optional.empty());

        service.markFailed(importId, "TEXTRACT_FAILED");
        service.markFailed(importId, "UNKNOWN");

        assertThat(item.getStatus()).isEqualTo("FAILED");
        assertThat(item.getErrorCode()).isEqualTo("TEXTRACT_FAILED");
        verify(repository).save(item);
    }
}
