package bflow.tranfers;

import bflow.auth.entities.User;
import bflow.tranfers.entities.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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
        AND (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId)
    """)
    Page<Transfer> findTransfersByWallet(
            UUID userId,
            UUID walletId,
            Pageable pageable
    );

    /**
     * Counts the total number of transfers where the wallet is either the
     * source or destination.
     *
     * @param walletId wallet identifier.
     * @return total transfer count.
     */
    @Query("""
    SELECT COUNT(t) FROM Transfer t
    WHERE t.fromWallet.id = :walletId
       OR t.toWallet.id = :walletId
""")
    long countByWallet(UUID walletId);

    /**
     * Retrieves the latest transfer creation timestamp where the wallet is
     * either the source or destination.
     *
     * @param walletId wallet identifier.
     * @return most recent creation timestamp, or {@code null} if no transfers
     *         exist.
     */
    @Query("""
    SELECT MAX(t.createdAt) FROM Transfer t
    WHERE t.fromWallet.id = :walletId
       OR t.toWallet.id = :walletId
""")
    Instant findMaxCreatedAtByWallet(UUID walletId);

    /**
     * Counts transfers where either wallet side belongs to the given
     * wallets, within a date range.
     *
     * @param walletIds the wallet IDs to filter by (matches either the
     *        source or destination wallet)
     * @param start the start instant of the range (inclusive), in UTC
     * @param end the end instant of the range (exclusive), in UTC
     * @return the number of transfers matching the wallets and date range
     */
    @Query("SELECT COUNT(t) FROM Transfer t "
            + "WHERE (t.fromWallet.id IN :walletIds "
            + "OR t.toWallet.id IN :walletIds) "
            + "AND t.createdAt >= :start AND t.createdAt < :end")
    long countByWalletsAndDateRange(
            @Param("walletIds") List<UUID> walletIds,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Modifying
    @Query("UPDATE Transfer t SET t.user = :toUser "
            + "WHERE t.user.id = :fromUserId "
            + "AND (t.fromWallet.id IN :walletIds "
            + "OR t.toWallet.id IN :walletIds)")
    int reassignContributor(
            @Param("fromUserId") UUID fromUserId,
            @Param("toUser") User toUser,
            @Param("walletIds") List<UUID> walletIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Transfer t "
            + "WHERE t.fromWallet.id IN :walletIds OR t.toWallet.id IN :walletIds")
    void deleteByWalletIds(@Param("walletIds") List<UUID> walletIds);
}
