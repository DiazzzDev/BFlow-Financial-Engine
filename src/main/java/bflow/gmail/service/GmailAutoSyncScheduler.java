package bflow.gmail.service;

import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.gmail.dto.GmailSyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically checks Gmail for users who selected a wallet and completed an initial sync. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmailAutoSyncScheduler {
    private static final int AUTOMATIC_LOOKBACK_DAYS = 1;
    private static final int AUTOMATIC_MAX_MESSAGES = 100;
    private final GmailIntegrationRepository integrationRepository;
    private final GmailIntegrationService integrationService;

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void synchronizeConnectedAccounts() {
        for (GmailIntegration integration
                : integrationRepository.findAllByStatusAndWalletIdIsNotNull("CONNECTED")) {
            try {
                integrationService.sync(integration.getUser().getId(),
                        new GmailSyncRequest(integration.getWalletId(),
                                AUTOMATIC_LOOKBACK_DAYS, AUTOMATIC_MAX_MESSAGES, false));
            } catch (RuntimeException failure) {
                // Do not log provider errors, tokens, message content, or attachment metadata.
                log.warn("Scheduled Gmail sync failed for integration {}", integration.getId());
            }
        }
    }
}
