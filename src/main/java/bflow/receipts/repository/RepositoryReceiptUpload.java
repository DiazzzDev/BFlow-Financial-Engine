package bflow.receipts.repository;

import bflow.auth.entities.User;
import bflow.receipts.entity.ReceiptUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for receipt upload records.
 */
@Repository
public interface RepositoryReceiptUpload
        extends JpaRepository<ReceiptUpload, UUID> {

    /**
     * Finds a receipt upload by its identifier and owner.
     *
     * @param id the receipt upload identifier
     * @param userId the identifier of the owning user
     * @return an optional containing the receipt upload if found
     */
    Optional<ReceiptUpload> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Checks whether a receipt upload exists for the given stored file.
     *
     * @param storedFileId the identifier of the stored file
     * @return true if a receipt upload exists for the stored file
     */
    boolean existsByStoredFileId(UUID storedFileId);

    /**
     * Finds a receipt upload by the Textract job that was submitted
     * for it. Used by the OCR result listener to correlate an SNS
     * completion notification (which only carries the JobId) back
     * to the receipt that requested it.
     *
     * @param textractJobId the Textract {@code JobId}
     * @return an optional containing the receipt upload if found
     */
    Optional<ReceiptUpload> findByTextractJobId(String textractJobId);

    /**
     * Finds a receipt upload by id with its {@code storedFile}
     * eagerly fetched, so the OCR request listener can read {@code
     * storedFile.objectKey} without needing an open Hibernate
     * session beyond this call — the listener's own transaction
     * only wraps the status transition, not the S3/Textract calls.
     *
     * @param id the receipt upload identifier
     * @return an optional containing the receipt upload if found
     */
    @Query("select r from ReceiptUpload r "
            + "join fetch r.storedFile where r.id = :id")
    Optional<ReceiptUpload> findByIdWithStoredFile(UUID id);

    List<ReceiptUpload> findByWalletIdIn(List<UUID> walletIds);
    void deleteByWalletIdIn(List<UUID> walletIds);

    @Modifying
    @Query("UPDATE ReceiptUpload r SET r.user = :toUser "
            + "WHERE r.user.id = :fromUserId AND r.wallet.id IN :walletIds")
    int reassignUser(
            @Param("fromUserId") UUID fromUserId,
            @Param("toUser") User toUser,
            @Param("walletIds") List<UUID> walletIds
    );
}
