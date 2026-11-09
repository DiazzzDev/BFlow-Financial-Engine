package Diaz.Dev.BFlow.gmail;

import bflow.auth.entities.User;
import bflow.gmail.dto.GmailSyncRequest;
import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.gmail.service.GmailAutoSyncScheduler;
import bflow.gmail.service.GmailIntegrationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GmailAutoSyncSchedulerTest {
    @Test
    void schedulesReadOnlySyncForPreviouslyAuthorizedWallet() {
        GmailIntegrationRepository integrations = mock(GmailIntegrationRepository.class);
        GmailIntegrationService service = mock(GmailIntegrationService.class);
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        GmailIntegration integration = new GmailIntegration();
        integration.setId(UUID.randomUUID());
        integration.setUser(user);
        integration.setWalletId(walletId);
        when(integrations.findAllByStatusAndWalletIdIsNotNull("CONNECTED"))
                .thenReturn(List.of(integration));

        new GmailAutoSyncScheduler(integrations, service).synchronizeConnectedAccounts();

        verify(service).sync(userId, new GmailSyncRequest(walletId, 1, 100, false));
    }
}
