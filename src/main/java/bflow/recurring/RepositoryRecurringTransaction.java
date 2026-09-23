package bflow.recurring;

import bflow.recurring.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Repository interface for RecurringTransaction entities.
 */
@Repository
public interface RepositoryRecurringTransaction
        extends JpaRepository<RecurringTransaction, UUID> {

    /**
     * Find all active recurring transactions that are due for execution.
     *
     * @param dueDate the date to check for due transactions
     * @return list of due recurring transactions
     */
    @Query("""
        SELECT r FROM RecurringTransaction r
        WHERE r.active = true
        AND r.nextExecutionDate <= :dueDate
        AND (r.endDate IS NULL OR r.endDate >= :dueDate)
    """)
    List<RecurringTransaction> findDueTransactions(LocalDate dueDate);

    /**
     * Find all recurring transactions for a specific user.
     *
     * @param userId the user ID
     * @return list of recurring transactions
     */
    List<RecurringTransaction> findByUserId(UUID userId);

    /**
     * Counts the number of active recurring transactions
     * owned by the specified user.
     *
     * @param userId the user identifier
     * @return the number of active recurring transactions
     */
    long countByUserIdAndActiveTrue(UUID userId);

    /**
     * Top N active recurring transactions for a wallet, nearest due date first.
     * Use Pageable.ofSize(3) to get the top 3.
     *
     * @param walletId the wallet identifier
     * @param pageable pagination (typically PageRequest.of(0, 3))
     * @return the list of upcoming recurring transactions
     */
    List<RecurringTransaction>
    findByWalletIdAndActiveTrueOrderByNextExecutionDateAsc(
            UUID walletId, Pageable pageable
    );

    /**
     * Checks whether a wallet has any recurring transaction defined,
     * active or not.
     *
     * @param walletId the wallet UUID
     * @return true if at least one recurring transaction exists
     */
    boolean existsByWalletId(UUID walletId);

    void deleteByUserId(UUID userId);
}
