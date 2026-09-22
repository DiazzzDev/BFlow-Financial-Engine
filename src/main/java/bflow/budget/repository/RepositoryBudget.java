package bflow.budget.repository;

import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.PeriodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Budget entities.
 */
@Repository
public interface RepositoryBudget extends JpaRepository<Budget, UUID>,
        JpaSpecificationExecutor<Budget> {
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

    /**
     * Counts all budgets for a specific user.
     *
     * @param userId the user ID
     * @return list of budgets
     */
    long countByUserId(UUID userId);

    /**
     * Checks whether a budget exists for the specified user, scope,
     * category, and period.
     *
     * @param userId   the ID of the user
     * @param scope    the budget scope
     * @param categoryId the ID of the category
     * @param period   the budget period
     * @return true if a matching budget exists, otherwise false
     */
    boolean existsByUserIdAndScopeAndCategoryIdAndPeriod(
            UUID userId, BudgetScope scope, UUID categoryId, PeriodType period
    );

    /**
     * Checks whether a budget exists for the specified user, scope,
     * category, and period, excluding the budget with the specified ID.
     *
     * @param userId   the ID of the user
     * @param scope    the budget scope
     * @param categoryId the ID of the category
     * @param period   the budget period
     * @param id       the ID of the budget to exclude
     * @return true if a matching budget exists, otherwise false
     */
    boolean existsByUserIdAndScopeAndCategoryIdAndPeriodAndIdNot(
            UUID userId, BudgetScope scope, UUID categoryId,
            PeriodType period, UUID id
    );

    /**
     * Finds budgets for the specified user, scope, and category.
     *
     * @param userId     the ID of the user
     * @param scope      the budget scope
     * @param categoryId the ID of the category
     * @return a list of matching budgets
     */
    List<Budget> findByUserIdAndScopeAndCategoryId(
            UUID userId, BudgetScope scope, UUID categoryId
    );

    /**
     * Finds the 3 most recently updated budgets for a user — used for the
     * "Budgets health" dashboard widget.
     *
     * @param userId the user ID
     * @return up to 3 budgets ordered by most recently updated
     */
    List<Budget> findTop3ByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<Budget> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
