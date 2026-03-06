package bflow.tranfers;

import bflow.tranfers.entities.Transfer;
import bflow.wallet.entities.WalletUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryTransfers extends JpaRepository<Transfer, UUID> {

    /**
     * Finds all transfers by user ID with pagination.
     * @param userId the user UUID.
     * @param pageable the pagination information.
     * @return a page of transfers relationships.
     */
    Page<Transfer> findByUserId(UUID userId, Pageable pageable);

    Optional<Transfer> findByIdAndUserId(UUID id, UUID userId);
}
