package bflow.wallet;

import bflow.wallet.entities.WalletUser;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryWalletUser extends JpaRepository<WalletUser, UUID> {
    /**
     * Finds a wallet-user relationship by wallet ID and user ID.
     * @param walletId the wallet UUID.
     * @param userId the user UUID.
     * @return optional wallet-user relationship.
     */
    Optional<WalletUser> findByWalletIdAndUserId(UUID walletId, UUID userId);

    /**
     * Finds wallet-user relationships by user ID with pagination.
     * @param userId the user UUID.
     * @param pageable the pagination information.
     * @return a page of wallet-user relationships.
     */
    Page<WalletUser> findByUserId(UUID userId, Pageable pageable);
}
