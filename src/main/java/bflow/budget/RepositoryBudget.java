package bflow.budget;

import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.PeriodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Budget entities.
 */
@Repository
public interface RepositoryBudget extends JpaRepository<Budget, UUID> {
    /**
     * Find all budgets for a specific wallet.
     *
     * @param walletId the wallet ID
     * @return list of budgets
     */
    List<Budget> findByWalletId(UUID walletId);

    /**
     * Find a budget by ID and user ID.
     *
     * @param budgetId the budget ID
     * @param userId the user ID
     * @return optional containing the budget if found
     */
    Optional<Budget> findByIdAndUserId(UUID budgetId, UUID userId);

    /**
     * Check if a budget exists with given wallet, user, scope, and period.
     *
     * @param walletId the wallet ID
     * @param userId the user ID
     * @param scope the budget scope
     * @param period the period type
     * @return true if such a budget exists
     */
    boolean existsByWalletIdAndUserIdAndScopeAndPeriod(
            UUID walletId,
            UUID userId,
            BudgetScope scope,
            PeriodType period
    );

    /**
     * Check if a budget exists with given wallet, user, category, and period.
     *
     * @param walletId the wallet ID
     * @param userId the user ID
     * @param categoryId the category ID
     * @param period the period type
     * @return true if such a budget exists
     */
    boolean existsByWalletIdAndUserIdAndCategoryIdAndPeriod(
            UUID walletId,
            UUID userId,
            UUID categoryId,
            PeriodType period
    );

    /**
     * Check if a budget exists with given wallet, user, scope, and period,
     * excluding a specific budget ID.
     *
     * @param walletId the wallet ID
     * @param userId the user ID
     * @param scope the budget scope
     * @param period the period type
     * @param id the budget ID to exclude
     * @return true if such a budget exists
     */
    boolean existsByWalletIdAndUserIdAndScopeAndPeriodAndIdNot(
            UUID walletId,
            UUID userId,
            BudgetScope scope,
            PeriodType period,
            UUID id
    );

    /**
     * Check if a budget exists with given wallet, user, category, and
     * period, excluding a specific budget ID.
     *
     * @param walletId the wallet ID
     * @param userId the user ID
     * @param categoryId the category ID
     * @param period the period type
     * @param id the budget ID to exclude
     * @return true if such a budget exists
     */
    boolean existsByWalletIdAndUserIdAndCategoryIdAndPeriodAndIdNot(
            UUID walletId,
            UUID userId,
            UUID categoryId,
            PeriodType period,
            UUID id
    );
}
