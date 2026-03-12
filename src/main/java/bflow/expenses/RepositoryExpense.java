package bflow.expenses;

import bflow.expenses.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface RepositoryExpense extends JpaRepository<Expense, UUID> {
    Page<Expense> findByWalletId(UUID walletId, Pageable pageable);
}
