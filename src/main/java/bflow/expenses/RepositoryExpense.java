package bflow.expenses;

import bflow.expenses.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface RepositoryExpense extends JpaRepository<Expense, UUID> {
    /**
    * Retrieves expenses belonging to a specific wallet.
    *
    * @param walletId the wallet identifier.
    * @param pageable pagination configuration.
    * @return a page containing wallet expenses.
    */
    Page<Expense> findByWalletId(UUID walletId, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.wallet.id = :walletId
        AND e.date BETWEEN :start AND :end
    """)
    BigDecimal sumExpensesByWalletAndDateRange(
            UUID walletId,
            LocalDate start,
            LocalDate end
    );
}
