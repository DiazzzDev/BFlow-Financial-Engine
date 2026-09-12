package bflow.tranfers;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.i18n.MessageService;
import bflow.tranfers.DTO.TransferenceRequest;
import bflow.tranfers.DTO.TransferenceResponse;
import bflow.tranfers.entities.Transfer;
import bflow.tranfers.enums.TransferStatus;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service class for managing transfer operations between wallets.
 * Handles transfer retrieval, creation, and validation logic.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ServiceTransfers {
    /** The repository for transfer database operations. */
    private final RepositoryTransfers repositoryTransfers;

    /** The repository for wallet-user relationship operations. */
    private final RepositoryWalletUser repositoryWalletUser;

    /** Repository for wallet persistence operations. */
    private final RepositoryWallet repositoryWallet;

    /** The repository for user database operations. */
    private final RepositoryUser repositoryUser;

    /** The service handling wallet business logic. */
    private final ServiceWallet serviceWallet;

    /** The service for user business logic. */
    private final UserService userService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Retrieves a transfer by its ID, validating user authorization.
     * @param transferId the unique identifier of the transfer.
     * @param userId the unique identifier of the user making the request.
     * @return the transfer response data.
     * @throws ResourceNotFoundException if transfer not found or unauthorized.
     */
    public TransferenceResponse getTransferById(
            final UUID transferId,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        Transfer transfer = repositoryTransfers
                .findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("transfer.notFound")
                ));
        return mapToResponse(transfer);
    }

    /**
     * Retrieves all transfers for a user with pagination.
     * @param userId the unique identifier of the user.
     * @param pageable the pagination information.
     * @return a page of transfer responses.
     */
    public Page<TransferenceResponse> getUserTransfers(
            final UUID userId,
            final Pageable pageable
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        Page<Transfer> page = repositoryTransfers
                .findByUserId(userId, pageable);
        return page.map(this::mapToResponse);
    }

    /**
     * Retrieves transfers for a specific wallet, validating user authorization.
     * @param userId the unique identifier of the user.
     * @param walletId the unique identifier of the wallet.
     * @param pageable the pagination information.
     * @return a page of transfer responses.
     */
    public Page<TransferenceResponse> getUserTransfersByWalletId(
            final UUID userId,
            final UUID walletId,
            final Pageable pageable
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        Page<Transfer> page = repositoryTransfers
                .findTransfersByWallet(userId, walletId, pageable);

        return page.map(this::mapToResponse);
    }

    /**
     * Processes a transfer between two wallets.
     * @param request the transfer request.
     * @param userId the user ID.
     * @return the transfer response.
     */
    public TransferenceResponse saveTransfer(
            final TransferenceRequest request,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        final BigDecimal amount = request.getAmount();

        // Retrieve authenticated user
        User user = repositoryUser.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageService.get("user.notFound")
                ));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    messageService.get("transfer.amount.mustBePositive")
            );
        }

        // Validate access: check if user is linked to this wallet
        WalletUser originWallet = repositoryWalletUser
                .findByWalletIdAndUserId(request.getFromWalletId(), userId)
                .orElseThrow(() -> new AccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        // Validate access: check if user is linked to this wallet
        WalletUser destinationWallet = repositoryWalletUser
                .findByWalletIdAndUserId(request.getToWalletId(), userId)
                .orElseThrow(() -> new AccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        UUID firstId = originWallet.getWallet().getId();
        UUID secondId = destinationWallet.getWallet().getId();

        Wallet walletA;
        Wallet walletB;

        if (firstId.compareTo(secondId) < 0) {
            walletA = repositoryWallet.findByIdForUpdate(firstId)
                    .orElseThrow(
                        () -> new ResourceNotFoundException(
                                messageService.get("wallet.origin.notFound")
                        )
                );
            walletB = repositoryWallet.findByIdForUpdate(secondId)
                    .orElseThrow(
                        () -> new ResourceNotFoundException(
                                messageService.get("wallet.target.notFound")
                        )
                );
        } else {
            walletB = repositoryWallet.findByIdForUpdate(secondId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("wallet.target.notFound")
                ));
            walletA = repositoryWallet.findByIdForUpdate(firstId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("wallet.origin.notFound")
                ));
        }

        Wallet fromWallet = firstId.equals(walletA.getId()) ? walletA : walletB;
        Wallet toWallet = fromWallet == walletA ? walletB : walletA;

        if (fromWallet.getId().equals(toWallet.getId())) {
            throw new IllegalStateException(
                    messageService.get("transfer.sameWallet")
            );
        }

        if (!fromWallet.getCurrency().equals(toWallet.getCurrency())) {
            throw new IllegalStateException(
                    messageService.get("transfer.currencyMismatch")
            );
        }

        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    messageService.get("wallet.balance.insufficientForAdjustment")
            );
        }

        serviceWallet.subtractBalance(fromWallet, amount);
        serviceWallet.addBalance(toWallet, amount);

        Transfer transfer = buildTransfer(
                fromWallet, toWallet, request, user
        );

        repositoryTransfers.save(transfer);

        return mapToResponse(transfer);
    }

    /**
     * Builds a Transfer entity from request data.
     * @param fromWallet the source wallet.
     * @param toWallet the destination wallet.
     * @param request the transfer request.
     * @param user the user executing the transfer.
     * @return the transfer entity.
     */
    private Transfer buildTransfer(
            final Wallet fromWallet,
            final Wallet toWallet,
            final TransferenceRequest request,
            final User user
            ) {
        Transfer transfer = new Transfer();
        transfer.setFromWallet(fromWallet);
        transfer.setToWallet(toWallet);
        transfer.setAmount(request.getAmount());
        transfer.setDescription(request.getDescription());
        transfer.setUser(user);
        transfer.setStatus(TransferStatus.COMPLETED);
        return transfer;
    }

    /**
     * Maps a Transfer entity to a TransferenceResponse DTO.
     * @param transfer the transfer entity.
     * @return the transfer response.
     */
    private TransferenceResponse mapToResponse(final Transfer transfer) {
        TransferenceResponse response = new TransferenceResponse();

        response.setId(transfer.getId());

        response.setFromWalletId(transfer.getFromWallet().getId());
        response.setFromWalletName(transfer.getFromWallet().getName());

        response.setToWalletId(transfer.getToWallet().getId());
        response.setToWalletName(transfer.getToWallet().getName());

        response.setAmount(transfer.getAmount());
        response.setDescription(transfer.getDescription());
        response.setStatus(transfer.getStatus().name());

        return response;
    }
}
