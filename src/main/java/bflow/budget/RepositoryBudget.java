package bflow.budget;

import bflow.budget.entity.Budget;
import bflow.budget.enums.PeriodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryBudget extends JpaRepository<Budget, UUID> {
    Optional<Budget> findByWalletIdAndUserIdAndPeriod(
            UUID walletId,
            UUID userId,
            PeriodType period
    );

    List<Budget> findByWalletId(UUID walletId);
}
