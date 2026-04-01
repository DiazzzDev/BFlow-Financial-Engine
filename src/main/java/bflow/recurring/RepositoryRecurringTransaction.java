package bflow.recurring;

import bflow.recurring.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
}
