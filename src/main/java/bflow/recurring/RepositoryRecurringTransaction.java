package bflow.recurring;

import bflow.recurring.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RepositoryRecurringTransaction extends JpaRepository<RecurringTransaction, UUID> {
    @Query("""
       SELECT r
       FROM RecurringTransaction r
       WHERE r.active = true
       AND r.nextExecutionDate <= :today
       """)
    List<RecurringTransaction> findDueTransactions(LocalDate today);

    List<RecurringTransaction> findByUserId(UUID userId);
}
