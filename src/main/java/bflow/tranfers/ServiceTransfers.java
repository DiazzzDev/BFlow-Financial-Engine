package bflow.tranfers;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.common.exception.ResourceNotFoundException;
import bflow.tranfers.DTO.TransferenceRequest;
import bflow.tranfers.DTO.TransferenceResponse;
import bflow.tranfers.entities.Transfer;
import bflow.tranfers.enums.TransferStatus;
import bflow.wallet.RepositoryWallet;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.ServiceWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service class for managing transfer operations between wallets.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ServiceTransfers {
    /** The repository for transfer database operations. */
    private final RepositoryTransfers repositoryTransfers;

    /** The repository for wallet database operations. */
    private final RepositoryWallet repositoryWallet;

    /** The repository for wallet-user relationship operations. */
    private final RepositoryWalletUser repositoryWalletUser;

    /** The repository for user database operations. */
    private final RepositoryUser repositoryUser;

    /** The service handling wallet business logic. */
    private final ServiceWallet serviceWallet;

    public TransferenceResponse getTransferById(
            final UUID transferId,
            final UUID userId
    ) {
        Transfer transfer = repositoryTransfers
                .findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transferencia no encontrada o no autorizada"));
        return mapToResponse(transfer);
    }

    public Page<TransferenceResponse> getUserTransfers(
            final UUID userId,
            final Pageable pageable
    ) {
        Page<Transfer> page = repositoryTransfers
                .findByUserId(userId, pageable);
        return page.map(this::mapToResponse);
    }

    public Page<TransferenceResponse> getUserTransfersByWalletId(
            final UUID userId,
            final UUID walletId,
            final Pageable pageable
    ) {

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
        final BigDecimal amount = request.getAmount();

        // Retrieve authenticated user
        User user = repositoryUser.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found"
                ));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Amount must be greater than zero");
        }

        // Validate access: check if user is linked to this wallet
        WalletUser originWallet = repositoryWalletUser
                .findByWalletIdAndUserId(request.getFromWalletId(), userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "User does not have access to origin this wallet"
                ));

        // Validate access: check if user is linked to this wallet
        WalletUser destinationWallet = repositoryWalletUser
                .findByWalletIdAndUserId(request.getToWalletId(), userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "User does not have access to this wallet"
                ));

        Wallet fromWallet = originWallet.getWallet();
        Wallet toWallet = destinationWallet.getWallet();

        if (fromWallet.getId().equals(toWallet.getId())) {
            throw new IllegalStateException(
                    "Cannot transfer to the same wallet"
            );
        }

        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
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
     * @return the transfer entity.
     */
    private Transfer buildTransfer(
            final Wallet fromWallet,
            final Wallet toWallet,
            final TransferenceRequest request,
            final User user
            ) {
        Transfer transfer = new Transfer();
        transfer.setFromWalletId(fromWallet);
        transfer.setToWalletId(toWallet);
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

        response.setFromWalletId(transfer.getFromWalletId().getId());
        response.setFromWalletName(transfer.getFromWalletId().getName());

        response.setToWalletId(transfer.getToWalletId().getId());
        response.setToWalletName(transfer.getToWalletId().getName());

        response.setAmount(transfer.getAmount());
        response.setDescription(transfer.getDescription());
        response.setStatus(transfer.getStatus().name());

        return response;
    }
}
