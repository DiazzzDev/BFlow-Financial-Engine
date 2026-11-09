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
        UUID walletId = request.walletId() == null
                ? walletUserRepository.findFirstByUserIdOrderByDefaultWalletDescCreatedAtAsc(userId)
                    .map(membership -> membership.getWallet().getId())
                    .orElseThrow(() -> new WalletAccessDeniedException(messageService.get("gmail.wallet.accessDenied")))
                : request.walletId();
        walletUserRepository.findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("gmail.wallet.accessDenied")));
        GmailIntegration integration = integrationRepository.findByUserIdAndStatus(userId, "CONNECTED")
                .orElseThrow(() -> new IllegalStateException(messageService.get("gmail.connection.required")));
        String accessToken;
        try {
            accessToken = oauthService.accessToken(integration);
        } catch (RuntimeException ex) {
            if (isRevoked(ex)) {
                integration.setStatus("REVOKED");
                integrationRepository.save(integration);
            }
            throw new IllegalStateException(messageService.get("gmail.authorization.invalid"));
        }

        if (!request.dryRun() && !walletId.equals(integration.getWalletId())) {
            integration.setWalletId(walletId);
            integrationRepository.save(integration);
        }

        List<String> messages;
        try {
            messages = gmailApiClient.listMessageIds(
                    accessToken, request.lookbackDays(), request.maxMessages());
        } catch (IOException failure) {
            throw new IllegalStateException(messageService.get("gmail.provider.unavailable"), failure);
        }
        int compatibleItems = 0;
        int duplicates = 0;
        int queued = 0;
        int errors = 0;
        for (String messageId : messages) {
            try {
                if (importRepository.existsByUserIdAndMessageId(userId, messageId)) {
                    duplicates++;
                    continue;
                }
                Optional<GmailApiClient.AttachmentRef> attachment = gmailApiClient
                        .listAttachments(accessToken, messageId).stream()
                        .filter(this::isEligibleAttachment)
                        .sorted(Comparator.comparingInt(this::attachmentPriority)
                                .thenComparing(GmailApiClient.AttachmentRef::size,
                                        Comparator.reverseOrder()))
                        .findFirst();
                if (attachment.isPresent()) {
                    compatibleItems++;
                    if (!request.dryRun()) {
                        enqueue(userId, walletId, integration, messageId,
                                attachment.get().attachmentId(), attachment.get().mimeType());
                        queued++;
                    }
                    continue;
                }
                for (GmailApiClient.TextPartRef textPart
                        : gmailApiClient.listTextParts(accessToken, messageId)) {
                    if (textPart.size() <= 0 || textPart.size() > 128 * 1024
                            || !emailTransactionParser.isTransactionEmail(textPart.preview())) {
                        continue;
                    }
                    compatibleItems++;
                    if (!request.dryRun()) {
                        enqueue(userId, walletId, integration, messageId,
                                textPart.partId(), textPart.mimeType());
                        queued++;
                    }
                    break;
                }
            } catch (DataIntegrityViolationException duplicate) {
                duplicates++;
            } catch (IOException | RuntimeException partialFailure) {
                errors++;
                log.warn("Gmail message discovery failed for user {}", userId);
            }
        }
        return new GmailSyncResponse(messages.size(), compatibleItems, duplicates, queued, errors);
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
