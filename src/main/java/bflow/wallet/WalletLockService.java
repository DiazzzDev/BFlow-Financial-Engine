package bflow.wallet;

import bflow.common.exception.ResourceNotFoundException;
import bflow.wallet.DTO.WalletPair;
import bflow.wallet.entities.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Acquires pessimistic locks on wallet rows in a deterministic order
 * (by UUID) to avoid deadlocks when two wallets are locked together.
 */
@Component
@RequiredArgsConstructor
public class WalletLockService {

    /** Repository used to fetch and lock wallet rows. */
    private final RepositoryWallet repositoryWallet;

    /**
     * Locks the two wallets involved in a transfer, always acquiring
     * locks in ascending UUID order to prevent deadlocks between
     * concurrent transfers that touch the same pair of wallets.
     *
     * @param oldWalletId id of the source wallet
     * @param newWalletId id of the destination wallet
     * @return the locked wallet pair (origin and target)
     * @throws ResourceNotFoundException if either wallet does not exist
     */
    public WalletPair lockWallets(
        final UUID oldWalletId,
        final UUID newWalletId
    ) {

        Wallet oldWallet;
        Wallet newWallet;

        if (oldWalletId.equals(newWalletId)) {
            oldWallet = repositoryWallet.findByIdForUpdate(oldWalletId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                "Wallet not found"
                        ));

            newWallet = oldWallet;

        } else if (oldWalletId.compareTo(newWalletId) < 0) {

            oldWallet = repositoryWallet.findByIdForUpdate(oldWalletId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                "Origin wallet not found"
                        ));

            newWallet = repositoryWallet.findByIdForUpdate(newWalletId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                "Target wallet not found"
                        ));

        } else {

            newWallet = repositoryWallet.findByIdForUpdate(newWalletId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                "Target wallet not found"
                        ));

            oldWallet = repositoryWallet.findByIdForUpdate(oldWalletId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                "Origin wallet not found"
                        ));
        }

        return new WalletPair(oldWallet, newWallet);
    }
}
