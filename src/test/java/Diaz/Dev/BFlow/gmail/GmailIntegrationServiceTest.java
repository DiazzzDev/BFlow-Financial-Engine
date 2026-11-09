package Diaz.Dev.BFlow.gmail;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.common.i18n.MessageService;
import bflow.gmail.dto.GmailSyncRequest;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.enums.GmailIntegrationStatus;
import bflow.gmail.enums.GmailImportStatus;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.gmail.service.GmailApiClient;
import bflow.gmail.service.GmailImportQueuePublisher;
import bflow.gmail.service.GmailIntegrationService;
import bflow.gmail.service.GmailOAuthService;
import bflow.gmail.service.GmailTokenCipher;
import bflow.gmail.service.GmailEmailTransactionParser;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GmailIntegrationServiceTest {
    @Mock private GmailIntegrationRepository integrationRepository;
    @Mock private GmailImportRepository importRepository;
    @Mock private GmailOAuthService oauthService;
    @Mock private GmailApiClient gmailApiClient;
    @Mock private GmailImportQueuePublisher queuePublisher;
    @Mock private GmailTokenCipher tokenCipher;
    @Mock private RepositoryWalletUser walletUserRepository;
    @Mock private RepositoryUser userRepository;
    @Mock private MessageService messageService;
    @Mock private GmailEmailTransactionParser emailTransactionParser;

    private GmailIntegrationService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GmailIntegrationService(integrationRepository, importRepository,
                oauthService, gmailApiClient, queuePublisher, tokenCipher,
                messageService, walletUserRepository, userRepository, emailTransactionParser);
    }

    @Test
    void reportsMessageAttachmentDuplicatesWithoutEnqueueingAgain() throws Exception {
        GmailIntegration integration = new GmailIntegration();
        integration.setId(UUID.randomUUID());
        integration.setStatus("CONNECTED");
        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(new Wallet());
        when(walletUserRepository.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.listMessageIds("access-token", 14, 20))
                .thenReturn(List.of("message-1"));
        when(importRepository.existsByUserIdAndMessageId(userId, "message-1"))
                .thenReturn(true);

        var response = service.sync(userId, new GmailSyncRequest(walletId, 14, 20, false));

        assertThat(response.messagesFound()).isEqualTo(1);
        assertThat(response.compatibleAttachments()).isZero();
        assertThat(response.duplicates()).isEqualTo(1);
        assertThat(response.queuedForProcessing()).isZero();
        verify(queuePublisher, never()).publish(any());
    }

    @Test
    void dryRunCountsCompatibleAttachmentsWithoutWritingOrEnqueueing() throws Exception {
        GmailIntegration integration = new GmailIntegration();
        integration.setId(UUID.randomUUID());
        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(new Wallet());
        when(walletUserRepository.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.listMessageIds("access-token", 7, 10)).thenReturn(List.of());

        var response = service.sync(userId, new GmailSyncRequest(walletId, 7, 10, true));

        assertThat(response.messagesFound()).isZero();
        verify(importRepository, never()).saveAndFlush(any());
        verify(queuePublisher, never()).publish(any());
    }

    @Test
    void refusesWalletNotAuthorizedForCurrentUser() throws Exception {
        when(walletUserRepository.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        GmailSyncRequest request = new GmailSyncRequest(walletId, 14, 20, true);
        assertThatThrownBy(() -> service.sync(userId, request))
                .isInstanceOf(RuntimeException.class);
        verify(gmailApiClient, never()).listMessageIds(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void marksConnectionRevokedWhenRefreshGrantIsInvalid() {
        GmailIntegration integration = new GmailIntegration();
        integration.setStatus("CONNECTED");
        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(new Wallet());
        when(walletUserRepository.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration))
                .thenThrow(new IllegalStateException("Gmail authorization was revoked"));
        when(messageService.get("gmail.authorization.invalid"))
                .thenReturn("Gmail authorization is invalid or revoked");

        GmailSyncRequest request = new GmailSyncRequest(walletId, 14, 20, false);
        assertThatThrownBy(() -> service.sync(userId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid or revoked");

        assertThat(integration.getStatus()).isEqualTo("REVOKED");
        verify(integrationRepository).save(integration);
    }

    @Test
    void returnsPartialErrorsAndStillQueuesOtherValidMessages() throws Exception {
        GmailIntegration integration = new GmailIntegration();
        integration.setId(UUID.randomUUID());
        integration.setStatus("CONNECTED");
        WalletUser walletUser = new WalletUser();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        walletUser.setWallet(wallet);
        User user = new User();
        user.setId(userId);
        when(walletUserRepository.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.listMessageIds("access-token", 14, 20))
                .thenReturn(List.of("bad-message", "good-message"));
        when(gmailApiClient.listAttachments("access-token", "bad-message"))
                .thenThrow(new IllegalStateException("provider failure with private payload"));
        when(gmailApiClient.listAttachments("access-token", "good-message"))
                .thenReturn(List.of(new GmailApiClient.AttachmentRef("attachment", "image/jpeg", 256)));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(importRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            bflow.gmail.entity.GmailImport item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        var response = service.sync(userId, new GmailSyncRequest(walletId, 14, 20, false));

        assertThat(response.messagesFound()).isEqualTo(2);
        assertThat(response.compatibleAttachments()).isEqualTo(1);
        assertThat(response.queuedForProcessing()).isEqualTo(1);
        assertThat(response.partialErrors()).isEqualTo(1);
        verify(queuePublisher, times(1)).publish(any());
    }

    @Test
    void queuesOnlyOnePreferredAttachmentForAMessageWithBodyAndMultipleAttachments() throws Exception {
        GmailIntegration integration = new GmailIntegration();
        integration.setId(UUID.randomUUID());
        integration.setStatus("CONNECTED");
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(wallet);
        User user = new User();
        user.setId(userId);
        when(walletUserRepository.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.listMessageIds("access-token", 14, 20))
                .thenReturn(List.of("message-1"));
        when(gmailApiClient.listAttachments("access-token", "message-1"))
                .thenReturn(List.of(
                        new GmailApiClient.AttachmentRef("payload-json", "application/json", 100),
                        new GmailApiClient.AttachmentRef("receipt-image", "image/png", 900),
                        new GmailApiClient.AttachmentRef("invoice-pdf", "application/pdf", 100)));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(importRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            GmailImport item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        var response = service.sync(userId, new GmailSyncRequest(walletId, 14, 20, false));

        assertThat(response.compatibleAttachments()).isEqualTo(1);
        assertThat(response.queuedForProcessing()).isEqualTo(1);
        verify(gmailApiClient, never()).listTextParts("access-token", "message-1");
        ArgumentCaptor<GmailImportQueuePublisher.ImportJob> job = ArgumentCaptor.forClass(
                GmailImportQueuePublisher.ImportJob.class);
        verify(queuePublisher, times(1)).publish(job.capture());
        assertThat(job.getValue().attachmentId()).isEqualTo("invoice-pdf");
    }

    @Test
    void fallsBackToOneTransactionBodyWhenNoCompatibleAttachmentExists() throws Exception {
        GmailIntegration integration = connectedIntegration();
        authorizedWallet();
        User user = new User();
        user.setId(userId);
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.listMessageIds("access-token", 14, 20)).thenReturn(List.of("message-body"));
        when(gmailApiClient.listAttachments("access-token", "message-body")).thenReturn(List.of());
        when(gmailApiClient.listTextParts("access-token", "message-body")).thenReturn(List.of(
                new GmailApiClient.TextPartRef("body:root", "text/plain", 64,
                        "Payment received. Amount: USD 14.20")));
        when(emailTransactionParser.isTransactionEmail("Payment received. Amount: USD 14.20")).thenReturn(true);
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(importRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            GmailImport item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        var response = service.sync(userId, new GmailSyncRequest(walletId, 14, 20, false));

        assertThat(response.compatibleAttachments()).isEqualTo(1);
        assertThat(response.queuedForProcessing()).isEqualTo(1);
        ArgumentCaptor<GmailImportQueuePublisher.ImportJob> job = ArgumentCaptor.forClass(
                GmailImportQueuePublisher.ImportJob.class);
        verify(queuePublisher).publish(job.capture());
        assertThat(job.getValue().attachmentId()).isEqualTo("body:root");
    }

    @Test
    void choosesDefaultWalletWhenRequestDoesNotSpecifyOne() throws Exception {
        GmailIntegration integration = connectedIntegration();
        WalletUser walletUser = authorizedWallet();
        when(walletUserRepository.findFirstByUserIdOrderByDefaultWalletDescCreatedAtAsc(userId))
                .thenReturn(Optional.of(walletUser));
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.listMessageIds("access-token", 7, 5)).thenReturn(List.of());

        var response = service.sync(userId, new GmailSyncRequest(null, 7, 5, true));

        assertThat(response.messagesFound()).isZero();
        verify(walletUserRepository).findFirstByUserIdOrderByDefaultWalletDescCreatedAtAsc(userId);
    }

    @Test
    void ignoresOversizedAndEmptyTextPartsWithoutParsingTheirContents() throws Exception {
        GmailIntegration integration = connectedIntegration();
        authorizedWallet();
        when(integrationRepository.findByUserIdAndStatus(userId, "CONNECTED"))
                .thenReturn(Optional.of(integration));
        when(oauthService.accessToken(integration)).thenReturn("access-token");
        when(gmailApiClient.listMessageIds("access-token", 14, 20)).thenReturn(List.of("message"));
        when(gmailApiClient.listAttachments("access-token", "message")).thenReturn(List.of());
        when(gmailApiClient.listTextParts("access-token", "message")).thenReturn(List.of(
                new GmailApiClient.TextPartRef("body:empty", "text/plain", 0, "private body"),
                new GmailApiClient.TextPartRef("body:large", "text/plain", 128 * 1024 + 1, "private body")));

        var response = service.sync(userId, new GmailSyncRequest(walletId, 14, 20, true));

        assertThat(response.compatibleAttachments()).isZero();
        assertThat(response.queuedForProcessing()).isZero();
        org.mockito.Mockito.verifyNoInteractions(emailTransactionParser);
    }

    @Test
    void reportsConnectionStatesAndMapsOnlyCurrentUsersImports() {
        GmailIntegration revoked = new GmailIntegration();
        revoked.setStatus("REVOKED");
        when(integrationRepository.findByUserId(userId)).thenReturn(Optional.of(revoked));
        assertThat(service.status(userId)).isEqualTo(new bflow.gmail.dto.GmailStatusResponse(
                false, GmailIntegrationStatus.REVOKED));
        when(integrationRepository.findByUserId(walletId)).thenReturn(Optional.empty());
        assertThat(service.status(walletId).status()).isEqualTo(GmailIntegrationStatus.DISCONNECTED);

        GmailImport pending = new GmailImport();
        pending.setId(UUID.randomUUID());
        pending.setStatus("QUEUED");
        when(importRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(pending));
        assertThat(service.imports(userId)).singleElement()
                .extracting(bflow.gmail.dto.GmailImportResponse::status)
                .isEqualTo(GmailImportStatus.QUEUED);
    }

    private GmailIntegration connectedIntegration() {
        GmailIntegration integration = new GmailIntegration();
        integration.setId(UUID.randomUUID());
        integration.setStatus("CONNECTED");
        integration.setWalletId(walletId);
        return integration;
    }

    private WalletUser authorizedWallet() {
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(wallet);
        when(walletUserRepository.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        return walletUser;
    }
}
