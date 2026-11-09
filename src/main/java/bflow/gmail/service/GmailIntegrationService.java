package bflow.gmail.service;

import bflow.auth.repository.RepositoryUser;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.i18n.MessageService;
import bflow.gmail.dto.GmailImportResponse;
import bflow.gmail.dto.GmailStatusResponse;
import bflow.gmail.dto.GmailSyncRequest;
import bflow.gmail.dto.GmailSyncResponse;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.enums.GmailImportStatus;
import bflow.gmail.enums.GmailIntegrationStatus;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailIntegrationService {
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    private final GmailIntegrationRepository integrationRepository;
    private final GmailImportRepository importRepository;
    private final GmailOAuthService oauthService;
    private final GmailApiClient gmailApiClient;
    private final GmailImportQueuePublisher queuePublisher;
    private final GmailTokenCipher tokenCipher;
    private final MessageService messageService;
    private final RepositoryWalletUser walletUserRepository;
    private final RepositoryUser userRepository;
    private final GmailEmailTransactionParser emailTransactionParser;

    public String authorizationUrl(final UUID userId) {
        return oauthService.createAuthorizationUrl(userId);
    }

    public String callback(final String code, final String state) {
        return oauthService.completeCallback(code, state);
    }

    @Transactional(readOnly = true)
    public GmailStatusResponse status(final UUID userId) {
        return integrationRepository.findByUserId(userId).map(integration -> {
            boolean connected = "CONNECTED".equals(integration.getStatus());
            return new GmailStatusResponse(connected, connected
                    ? GmailIntegrationStatus.CONNECTED : GmailIntegrationStatus.REVOKED);
        }).orElseGet(() -> new GmailStatusResponse(false, GmailIntegrationStatus.DISCONNECTED));
    }

    public GmailSyncResponse sync(final UUID userId, final GmailSyncRequest request) {
        UUID walletId = resolveWalletId(userId, request);
        walletUserRepository.findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("gmail.wallet.accessDenied")));
        GmailIntegration integration = integrationRepository.findByUserIdAndStatus(userId, "CONNECTED")
                .orElseThrow(() -> new IllegalStateException(messageService.get("gmail.connection.required")));
        String accessToken = accessToken(integration);
        if (!request.dryRun() && !walletId.equals(integration.getWalletId())) {
            integration.setWalletId(walletId);
            integrationRepository.save(integration);
        }

        List<String> messages = discoverMessages(accessToken, request);
        SyncTotals totals = new SyncTotals();
        for (String messageId : messages) {
            totals.add(processMessage(userId, walletId, integration, accessToken, messageId, request.dryRun()));
        }
        return new GmailSyncResponse(messages.size(), totals.compatible, totals.duplicates,
                totals.queued, totals.errors);
    }

    @Transactional
    public void disconnect(final UUID userId) {
        integrationRepository.findByUserId(userId).ifPresent(integration -> {
            oauthService.revoke(tokenCipher.decrypt(integration.getEncryptedRefreshToken()));
            integrationRepository.delete(integration);
        });
    }

    @Transactional(readOnly = true)
    public List<GmailImportResponse> imports(final UUID userId) {
        return importRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(item -> new GmailImportResponse(item.getId(), GmailImportStatus.valueOf(item.getStatus()),
                        item.getTransactionId(), item.getExpenseId(), item.getTransactionType(),
                        item.getErrorCode(), item.getCreatedAt()))
                .toList();
    }

    private boolean isRevoked(final RuntimeException ex) {
        return ex.getMessage() != null
                && (ex.getMessage().toLowerCase().contains("invalid_grant")
                || ex.getMessage().toLowerCase().contains("revoked"));
    }

    private UUID resolveWalletId(final UUID userId, final GmailSyncRequest request) {
        if (request.walletId() != null) return request.walletId();
        return walletUserRepository.findFirstByUserIdOrderByDefaultWalletDescCreatedAtAsc(userId)
                .map(membership -> membership.getWallet().getId())
                .orElseThrow(() -> new WalletAccessDeniedException(messageService.get("gmail.wallet.accessDenied")));
    }

    private String accessToken(final GmailIntegration integration) {
        try {
            return oauthService.accessToken(integration);
        } catch (RuntimeException failure) {
            if (isRevoked(failure)) {
                integration.setStatus("REVOKED");
                integrationRepository.save(integration);
            }
            throw new IllegalStateException(messageService.get("gmail.authorization.invalid"));
        }
    }

    private List<String> discoverMessages(final String accessToken, final GmailSyncRequest request) {
        try {
            return gmailApiClient.listMessageIds(accessToken, request.lookbackDays(), request.maxMessages());
        } catch (IOException failure) {
            throw new IllegalStateException(messageService.get("gmail.provider.unavailable"), failure);
        }
    }

    private MessageResult processMessage(final UUID userId, final UUID walletId,
            final GmailIntegration integration, final String accessToken, final String messageId,
            final boolean dryRun) {
        try {
            if (importRepository.existsByUserIdAndMessageId(userId, messageId)) return MessageResult.duplicateResult();
            Optional<GmailApiClient.AttachmentRef> attachment = preferredAttachment(accessToken, messageId);
            if (attachment.isPresent()) {
                GmailApiClient.AttachmentRef selected = attachment.get();
                return enqueueIfNeeded(userId, walletId, integration, messageId,
                        selected.attachmentId(), selected.mimeType(), dryRun);
            }
            return processTextBody(userId, walletId, integration, accessToken, messageId, dryRun);
        } catch (DataIntegrityViolationException duplicate) {
            return MessageResult.duplicateResult();
        } catch (IOException | RuntimeException partialFailure) {
            log.warn("Gmail message discovery failed for user {}", userId);
            return MessageResult.errorResult();
        }
    }

    private Optional<GmailApiClient.AttachmentRef> preferredAttachment(
            final String accessToken, final String messageId) throws IOException {
        return gmailApiClient.listAttachments(accessToken, messageId).stream()
                .filter(this::isEligibleAttachment)
                .sorted(Comparator.comparingInt(this::attachmentPriority)
                        .thenComparing(GmailApiClient.AttachmentRef::size, Comparator.reverseOrder()))
                .findFirst();
    }

    private MessageResult processTextBody(final UUID userId, final UUID walletId,
            final GmailIntegration integration, final String accessToken, final String messageId,
            final boolean dryRun) throws IOException {
        Optional<GmailApiClient.TextPartRef> transactionPart = gmailApiClient
                .listTextParts(accessToken, messageId).stream()
                .filter(this::isEligibleTextPart)
                .findFirst();
        if (transactionPart.isEmpty()) return MessageResult.none();
        GmailApiClient.TextPartRef part = transactionPart.get();
        return enqueueIfNeeded(userId, walletId, integration, messageId,
                part.partId(), part.mimeType(), dryRun);
    }

    private boolean isEligibleTextPart(final GmailApiClient.TextPartRef textPart) {
        return textPart.size() > 0 && textPart.size() <= 128 * 1024
                && emailTransactionParser.isTransactionEmail(textPart.preview());
    }

    private MessageResult enqueueIfNeeded(final UUID userId, final UUID walletId,
            final GmailIntegration integration, final String messageId,
            final String partId, final String mimeType, final boolean dryRun) {
        if (dryRun) return MessageResult.compatibleOnly();
        enqueue(userId, walletId, integration, messageId, partId, mimeType);
        return MessageResult.queuedResult();
    }

    private record MessageResult(int compatible, int duplicates, int queued, int errors) {
        private static MessageResult none() { return new MessageResult(0, 0, 0, 0); }
        private static MessageResult duplicateResult() { return new MessageResult(0, 1, 0, 0); }
        private static MessageResult errorResult() { return new MessageResult(0, 0, 0, 1); }
        private static MessageResult compatibleOnly() { return new MessageResult(1, 0, 0, 0); }
        private static MessageResult queuedResult() { return new MessageResult(1, 0, 1, 0); }
    }

    private static final class SyncTotals {
        private int compatible;
        private int duplicates;
        private int queued;
        private int errors;

        private void add(final MessageResult result) {
            compatible += result.compatible();
            duplicates += result.duplicates();
            queued += result.queued();
            errors += result.errors();
        }
    }

    private boolean isEligibleAttachment(final GmailApiClient.AttachmentRef attachment) {
        return attachment.mimeType() != null
                && ALLOWED_MIME_TYPES.contains(attachment.mimeType().toLowerCase(Locale.ROOT))
                && attachment.size() > 0 && attachment.size() <= MAX_ATTACHMENT_BYTES;
    }

    private int attachmentPriority(final GmailApiClient.AttachmentRef attachment) {
        return "application/pdf".equalsIgnoreCase(attachment.mimeType()) ? 0 : 1;
    }

    private void enqueue(final UUID userId, final UUID walletId,
                         final GmailIntegration integration, final String messageId,
                         final String attachmentId, final String mimeType) {
        GmailImport item = new GmailImport();
        item.setUser(userRepository.getReferenceById(userId));
        item.setIntegration(integration);
        item.setWallet(walletUserRepository.findByWalletIdAndUserId(
                walletId, userId).orElseThrow().getWallet());
        item.setMessageId(messageId);
        item.setMessageDedupKey(userId + ":" + messageId);
        item.setAttachmentId(attachmentId);
        item.setStatus("QUEUED");
        try {
            GmailImport saved = importRepository.saveAndFlush(item);
            queuePublisher.publish(new GmailImportQueuePublisher.ImportJob(
                    saved.getId(), userId, walletId, integration.getId(),
                    messageId, attachmentId, mimeType));
        } catch (RuntimeException queueFailure) {
            importRepository.delete(item);
            throw queueFailure;
        }
    }
}
