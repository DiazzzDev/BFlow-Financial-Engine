package Diaz.Dev.BFlow.gmail;

import bflow.auth.entities.User;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.gmail.service.GmailApiClient;
import bflow.gmail.service.GmailExpenseRegistrar;
import bflow.gmail.service.GmailImportProcessor;
import bflow.gmail.service.GmailImportQueuePublisher;
import bflow.gmail.service.GmailImportStateService;
import bflow.gmail.service.GmailOAuthService;
import bflow.gmail.service.GmailEmailTransactionParser;
import bflow.common.aws.service.StorageService;
import bflow.receipts.service.TextractExpenseJobStarter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GmailImportProcessorTest {
    @Mock private GmailImportRepository importRepository;
    @Mock private GmailIntegrationRepository integrationRepository;
    @Mock private GmailImportStateService stateService;
    @Mock private GmailOAuthService oauthService;
    @Mock private GmailApiClient gmailApiClient;
    @Mock private StorageService storageService;
    @Mock private TextractExpenseJobStarter textractJobStarter;
    @Mock private GmailExpenseRegistrar expenseRegistrar;
    @Mock private GmailEmailTransactionParser emailTransactionParser;

    private GmailImportProcessor processor;
    private GmailImport item;
    private GmailIntegration integration;
    private final UUID importId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID integrationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new GmailImportProcessor(importRepository, integrationRepository,
                stateService, oauthService, gmailApiClient, storageService,
                textractJobStarter, expenseRegistrar, emailTransactionParser);
        item = new GmailImport();
        item.setId(importId);
        item.setUser(new User());
        item.getUser().setId(userId);
        item.setMessageId("message");
        item.setAttachmentId("attachment");
        integration = new GmailIntegration();
        integration.setId(integrationId);
        integration.setUser(item.getUser());
        integration.setStatus("CONNECTED");
    }

    @Test
    void rejectsMimeSignatureMismatchBeforeTextract() throws Exception {
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(item));
        when(stateService.claim(importId)).thenReturn(true);
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.downloadAttachment("access-token", "message", "attachment"))
                .thenReturn(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});

        processor.process(new GmailImportQueuePublisher.ImportJob(importId, userId,
                UUID.randomUUID(), integrationId, "message", "attachment", "image/png"));

        verify(stateService).markFailed(importId, "ATTACHMENT_MIME_INVALID");
        verify(textractJobStarter, never()).start(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(expenseRegistrar, never()).register(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void idempotentQueueRedeliveryDoesNotFetchGmailOrRunTextractAgain() throws Exception {
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(item));
        when(stateService.claim(importId)).thenReturn(false);

        processor.process(new GmailImportQueuePublisher.ImportJob(importId, userId,
                UUID.randomUUID(), integrationId, "message", "attachment", "image/png"));

        verify(gmailApiClient, never()).downloadAttachment(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(textractJobStarter, never()).start(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

}
