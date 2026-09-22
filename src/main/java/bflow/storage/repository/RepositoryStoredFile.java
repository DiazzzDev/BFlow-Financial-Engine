package bflow.storage.repository;

import bflow.auth.entities.User;
import bflow.storage.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for stored file records.
 */
@Repository
public interface RepositoryStoredFile
        extends JpaRepository<StoredFile, UUID> {

    /**
     * Finds a file by id, scoped to its owner. Callers must use this
     * method instead of {@link #findById(Object)} whenever the file
     * is being resolved on behalf of an authenticated user, so that
     * a user can never access another user's file by id.
     *
     * @param id the stored file identifier
     * @param userId the identifier of the requesting user
     * @return an optional containing the file if it exists and is
     *         owned by the given user
     */
    Optional<StoredFile> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Finds a file by its S3 object key.
     *
     * @param objectKey the S3 object key
     * @return an optional containing the file if found
     */
    Optional<StoredFile> findByObjectKey(String objectKey);

    /**
     * Checks whether a file record already exists for the given
     * object key.
     *
     * @param objectKey the S3 object key
     * @return true if a record exists for that key
     */
    boolean existsByObjectKey(String objectKey);

    /**
     * Deletes orphaned stored file records and returns their S3 object keys.
     * Pending files older than the pending cutoff and uploaded files older
     * than the unreferenced cutoff are removed, but only if they are not
     * referenced by an expense, an income, or a receipt_uploads row (the
     * OCR bridge table also holds a FK to stored_files).
     *
     * @param pendingCutoff cutoff timestamp for pending files
     * @param unreferencedCutoff timestamp for uploaded unreferenced files
     * @return the S3 object keys of the deleted file records
     */
    @Modifying
    @Query(value = """
    WITH orphaned AS (
        SELECT sf.id, sf.object_key
        FROM stored_files sf
        WHERE (sf.status = 'PENDING' AND sf.created_at < :pendingCutoff)
           OR (sf.status = 'UPLOADED' AND sf.created_at < :unreferencedCutoff
               AND NOT EXISTS (
                   SELECT 1 FROM expenses e
                   WHERE e.receipt_file_id = sf.id
               )
               AND NOT EXISTS (
                   SELECT 1 FROM incomes i
                   WHERE i.receipt_file_id = sf.id
               )
               AND NOT EXISTS (
                   SELECT 1 FROM receipt_uploads ru
                   WHERE ru.stored_file_id = sf.id
               ))
    )
    DELETE FROM stored_files sf
    USING orphaned o
    WHERE sf.id = o.id
    RETURNING o.object_key
    """, nativeQuery = true)
    List<String> deleteOrphanedAndReturnKeys(
            @Param("pendingCutoff") Instant pendingCutoff,
            @Param("unreferencedCutoff") Instant unreferencedCutoff);

    /**
     * Reassigns StoredFile ownership to the ghost user for every file
     * still referenced as a receipt by a surviving (reassigned) expense
     * in the given wallets.
     */
    @Modifying
    @Query("UPDATE StoredFile sf SET sf.user = :toUser "
            + "WHERE sf.user.id = :fromUserId "
            + "AND sf.id IN ("
            + "  SELECT e.receiptFile.id FROM Expense e "
            + "  WHERE e.wallet.id IN :walletIds AND e.receiptFile IS NOT NULL"
            + ")")
    int reassignReceiptOwnersForWallets(
            @Param("fromUserId") UUID fromUserId,
            @Param("toUser") User toUser,
            @Param("walletIds") List<UUID> walletIds
    );

    /**
     * Finds the object keys of stored files owned by the user that are
     * used as receipts by expenses/incomes in the given wallets —
     * called BEFORE deleting those expenses, so the keys are still
     * resolvable for the S3 cleanup that follows.
     */
    @Query("""
        SELECT sf.objectKey
        FROM StoredFile sf
        WHERE sf.user.id = :userId
          AND (
              EXISTS (
                  SELECT 1
                  FROM Expense e
                  WHERE e.receiptFile.id = sf.id
                    AND e.wallet.id IN :walletIds
              )
              OR EXISTS (
                  SELECT 1
                  FROM Income i
                  WHERE i.receiptFile.id = sf.id
                    AND i.wallet.id IN :walletIds
              )
          )
    """)
    List<String> findReceiptObjectKeysForWallets(
        @Param("userId") UUID userId,
        @Param("walletIds") List<UUID> walletIds
    );

    void deleteByObjectKeyIn(List<String> objectKeys);
}
