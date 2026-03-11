package bflow.tranfers;

import bflow.tranfers.entities.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryTransfers extends JpaRepository<Transfer, UUID> {

    /**
     * Finds all transfers by user ID with pagination.
     * @param userId the user UUID.
     * @param pageable the pagination information.
     * @return a page of transfer relationships.
     */
    Page<Transfer> findByUserId(UUID userId, Pageable pageable);

    /**
     * Finds a transfer by its ID and user ID for access validation.
     * @param id the transfer UUID.
     * @param userId the user UUID.
     * @return an Optional containing the transfer if found and authorized.
     */
    Optional<Transfer> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Finds transfers by wallet ID with pagination for a user.
     * @param userId the user UUID.
     * @param walletId the wallet UUID.
     * @param pageable the pagination information.
     * @return a page of transfers matching the criteria.
     */
    @Query("""
        SELECT t
        FROM Transfer t
        WHERE t.user.id = :userId
        AND (t.fromWalletId.id = :walletId OR t.toWalletId.id = :walletId)
    """)
    Page<Transfer> findTransfersByWallet(
            UUID userId,
            UUID walletId,
            Pageable pageable
    );
}
