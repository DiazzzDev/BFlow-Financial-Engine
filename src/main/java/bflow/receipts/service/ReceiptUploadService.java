package bflow.receipts.service;

import bflow.auth.repository.RepositoryUser;
import bflow.common.aws.service.StorageService;
import bflow.common.exception.FileAccessDeniedException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.i18n.MessageService;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.services.ServiceExpense;
import bflow.income.DTO.IncomeRequest;
import bflow.income.ServiceIncome;
import bflow.receipts.DTO.ReceiptConfirmRequest;
import bflow.receipts.DTO.ReceiptUploadRequest;
import bflow.receipts.DTO.ReceiptUploadResponse;
import bflow.receipts.entity.ReceiptUpload;
import bflow.receipts.enums.ReceiptStatus;
import bflow.receipts.enums.ReceiptTransactionType;
import bflow.receipts.event.ReceiptRegisteredEvent;
import bflow.receipts.repository.RepositoryReceiptUpload;
import bflow.storage.entity.StoredFile;
import bflow.storage.enums.FileStatus;
import bflow.storage.repository.RepositoryStoredFile;
import bflow.wallet.entities.Wallet;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiptUploadService {

    /**
     * Repository for persisting and querying receipt upload
     * records.
     */
    private final RepositoryReceiptUpload repositoryReceiptUpload;

    /**
     * Repository for looking up the uploaded file backing a
     * receipt.
     */
    private final RepositoryStoredFile repositoryStoredFile;

    /**
     * Repository for verifying the user has access to a wallet.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Repository for obtaining a reference to the current user.
     */
    private final RepositoryUser repositoryUser;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Service used to create the Expense a receipt is confirmed
     * into.
     */
    private final ServiceExpense serviceExpense;

    /**
     * Service used to create the Income a receipt is confirmed
     * into.
     */
    private final ServiceIncome serviceIncome;

    /**
     * Service used to delete the underlying file when a receipt is
     * discarded.
     */
    private final StorageService storageService;

    /**
     * Publisher used to raise the domain event that triggers async
     * OCR processing once this transaction commits.
     */
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Registers an already-uploaded file as a receipt pending OCR
     * processing. This is the endpoint the "camera-first" flow hits
     * right after the user picks a wallet for the photo they just
     * took — no title, amount, or category exists yet.
     *
     * @param userId the id of the user registering the receipt
     * @param request the file and wallet to register the receipt
     *         against
     * @return the newly registered receipt, in RECEIVED status
     * @throws FileAccessDeniedException if the file doesn't belong
     *         to the user or isn't UPLOADED yet
     * @throws WalletAccessDeniedException if the wallet doesn't
     *         belong to the user
     * @throws IllegalStateException if the file was already
     *         registered as a receipt
     */
    @Transactional
    public ReceiptUploadResponse register(
            final UUID userId, final ReceiptUploadRequest request
    ) {
        StoredFile file = repositoryStoredFile
                .findByIdAndUserId(request.getFileId(), userId)
                .orElseThrow(() -> new FileAccessDeniedException(
                        messageService.get("receipt.file.accessDenied")));

        if (file.getStatus() != FileStatus.UPLOADED) {
            throw new IllegalStateException(
                    messageService.get(
                            "receipt.file.notReadyWithStatus",
                            file.getStatus()
                    ));
        }

        if (repositoryReceiptUpload.existsByStoredFileId(file.getId())) {
            throw new IllegalStateException(
                    messageService.get("receipt.alreadyRegistered"));
        }

        Wallet wallet = repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(), userId)
                .map(walletUser -> walletUser.getWallet())
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("receipt.wallet.accessDenied")));

        ReceiptUpload receipt = new ReceiptUpload();
        receipt.setUser(repositoryUser.getReferenceById(userId));
        receipt.setStoredFile(file);
        receipt.setWallet(wallet);
        receipt.setStatus(ReceiptStatus.RECEIVED);

        ReceiptUpload saved = repositoryReceiptUpload.save(receipt);

        // Actual OCR processing (SQS publish) only happens after
        // this transaction commits — see
        // ReceiptOcrRequestEventListener. Status stays RECEIVED
        // until the request listener picks it up.
        applicationEventPublisher.publishEvent(
                new ReceiptRegisteredEvent(saved.getId()));

        return toResponse(saved);
    }

    /**
     * Lets the frontend poll for status while OCR processes the
     * receipt, so the UX can show "processing..." and then navigate
     * to the resulting expense once ready.
     *
     * @param userId the id of the user requesting the status
     * @param receiptId the id of the receipt to look up
     * @return the current state of the receipt
     * @throws FileAccessDeniedException if the receipt doesn't
     *         belong to the user
     */
    @Transactional(readOnly = true)
    public ReceiptUploadResponse getStatus(
            final UUID userId, final UUID receiptId
    ) {
        ReceiptUpload receipt = repositoryReceiptUpload
                .findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new FileAccessDeniedException(
                        "Receipt not found or access denied"));

        return toResponse(receipt);
    }

    /**
     * Confirms a receipt's suggested data — as edited by the user —
     * into a new Expense or Income, and links the receipt to it.
     *
     * @param userId the id of the user confirming the receipt
     * @param receiptId the id of the receipt being confirmed
     * @param request the confirmed transaction data
     * @return the receipt, now in CONFIRMED status
     * @throws FileAccessDeniedException if the receipt doesn't
     *         belong to the user
     * @throws IllegalStateException if the receipt isn't in
     *         EXTRACTED status
     */
    @Transactional
    public ReceiptUploadResponse confirm(
            final UUID userId, final UUID receiptId,
            final ReceiptConfirmRequest request
    ) {
        ReceiptUpload receipt = repositoryReceiptUpload
                .findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new FileAccessDeniedException(
                        messageService.get("receipt.accessDenied")));

        if (receipt.getStatus() != ReceiptStatus.EXTRACTED) {
            throw new IllegalStateException(
                    messageService.get(
                            "receipt.cannotConfirm.wrongStatus",
                            receipt.getStatus()
                    ));
        }

        UUID resultingId;

        if (request.getType() == ReceiptTransactionType.EXPENSE) {
            ExpenseRequest expenseRequest = new ExpenseRequest();
            expenseRequest.setWalletId(receipt.getWallet().getId());
            expenseRequest.setCategoryId(request.getCategoryId());
            expenseRequest.setTitle(request.getTitle());
            expenseRequest.setDescription(request.getDescription());
            expenseRequest.setAmount(request.getAmount());
            expenseRequest.setDate(request.getDate());
            expenseRequest.setTaxDeductible(
                    Boolean.TRUE.equals(request.getTaxDeductible()));
            expenseRequest.setReimbursable(
                    Boolean.TRUE.equals(request.getReimbursable()));
            expenseRequest.setReceiptFileId(receipt.getStoredFile().getId());

            resultingId = UUID.fromString(
                    serviceExpense.newExpense(expenseRequest, userId).getId());
        } else {
            IncomeRequest incomeRequest = new IncomeRequest();
            incomeRequest.setWalletId(receipt.getWallet().getId());
            incomeRequest.setCategoryId(request.getCategoryId());
            incomeRequest.setTitle(request.getTitle());
            incomeRequest.setDescription(request.getDescription());
            incomeRequest.setAmount(request.getAmount());
            incomeRequest.setDate(request.getDate());
            incomeRequest.setTaxable(Boolean.TRUE.equals(request.getTaxable()));
            incomeRequest.setReceiptFileId(receipt.getStoredFile().getId());

            resultingId = UUID.fromString(
                    serviceIncome.newIncome(incomeRequest, userId).getId());
        }

        receipt.setStatus(ReceiptStatus.CONFIRMED);
        receipt.setResultingTransactionType(request.getType());
        receipt.setResultingTransactionId(resultingId);

        return toResponse(receipt);
    }

    /**
     * Discards a receipt the user doesn't want to keep, deleting
     * its underlying file immediately rather than waiting for the
     * cleanup job.
     *
     * @param userId the id of the user discarding the receipt
     * @param receiptId the id of the receipt to discard
     * @throws FileAccessDeniedException if the receipt doesn't
     *         belong to the user
     * @throws IllegalStateException if the receipt is already
     *         CONFIRMED
     */
    @Transactional
    public void discard(final UUID userId, final UUID receiptId) {
        ReceiptUpload receipt = repositoryReceiptUpload
                .findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new FileAccessDeniedException(
                        messageService.get("receipt.accessDenied")));

        if (receipt.getStatus() == ReceiptStatus.CONFIRMED) {
            throw new IllegalStateException(
                    messageService.get("receipt.confirmed.cannotDiscard"));
        }

        receipt.setStatus(ReceiptStatus.DISCARDED);

        // Limpieza inmediata: el usuario dijo explícitamente "no
        // quiero esto", no hay razón para esperar al cleanup job.
        storageService.delete(receipt.getStoredFile().getObjectKey());
    }

    private ReceiptUploadResponse toResponse(final ReceiptUpload receipt) {
        return new ReceiptUploadResponse(
                receipt.getId(),
                receipt.getStoredFile().getId(),
                receipt.getWallet().getId(),
                receipt.getStatus(),
                receipt.getSuggestedType(),
                receipt.getSuggestedTitle(),
                receipt.getSuggestedAmount(),
                receipt.getSuggestedCategoryId(),
                receipt.getSuggestedDate(),
                receipt.getConfidenceScore(),
                receipt.getFailureReason(),
                receipt.getResultingTransactionId(),
                receipt.getCreatedAt()
        );
    }
}
