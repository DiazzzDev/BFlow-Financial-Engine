package bflow.gmail.service;

import bflow.gmail.repository.GmailImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class GmailImportStateService {
    private final GmailImportRepository repository;

    public boolean claim(final UUID importId) {
        Instant now = Instant.now();
        return repository.claimQueued(importId, now, now.minus(10, ChronoUnit.MINUTES)) == 1;
    }

    @Transactional
    public void markProcessing(final UUID importId, final String objectKey, final String textractJobId) {
        repository.findById(importId).ifPresent(item -> {
            item.setS3ObjectKey(objectKey);
            item.setTextractJobId(textractJobId);
            item.setStatus("PROCESSING");
            item.setErrorCode(null);
            repository.save(item);
        });
    }

    @Transactional
    public void markFailed(final UUID importId, final String code) {
        repository.findById(importId).ifPresent(item -> {
            item.setStatus("FAILED");
            item.setErrorCode(code);
            repository.save(item);
        });
    }

}
