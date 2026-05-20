package bflow.budget.services;

import bflow.budget.DTO.BudgetRequest;
import bflow.budget.RepositoryBudget;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.PeriodType;
import bflow.common.exception.BudgetOverlapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public final class BudgetOverlapValidationService {

    /**
     * Repository for accessing Budget entities.
     */
    private final RepositoryBudget repositoryBudget;

    /**
     * Validate that creating a new budget does not overlap with existing ones.
     *
     * @param request the budget creation request
     * @param userId the ID of the user (owner)
     * @throws BudgetOverlapException if a budget already exists for this
     *         scope and period
     */
    public void validateCreateOverlap(
            final BudgetRequest request,
            final UUID userId
    ) {

        boolean exists;

        if (request.getScope() == BudgetScope.WALLET) {

            exists = repositoryBudget
                    .existsByWalletIdAndUserIdAndScopeAndPeriod(
                            request.getWalletId(),
                            userId,
                            request.getScope(),
                            request.getPeriod()
                    );

        } else {

            exists = repositoryBudget
                    .existsByWalletIdAndUserIdAndCategoryIdAndPeriod(
                            request.getWalletId(),
                            userId,
                            request.getCategoryId(),
                            request.getPeriod()
                    );
        }

        if (exists) {
            throw new BudgetOverlapException(
                    "A budget already exists for this scope and period"
            );
        }
    }

    /**
     * Validate that patching a budget does not create overlap with other
     * budgets.
     *
     * @param budget the budget entity being updated
     * @param scope the new budget scope
     * @param categoryId the new category ID
     * @param period the new period type
     * @param userId the ID of the user (owner)
     * @throws BudgetOverlapException if update creates an overlap
     */
    public void validatePatchOverlap(
            final Budget budget,
            final BudgetScope scope,
            final UUID categoryId,
            final PeriodType period,
            final UUID userId
    ) {

        boolean exists;

        if (scope == BudgetScope.WALLET) {

            exists = repositoryBudget
                    .existsByWalletIdAndUserIdAndScopeAndPeriodAndIdNot(
                            budget.getWallet().getId(),
                            userId,
                            scope,
                            period,
                            budget.getId()
                    );

        } else {

            exists = repositoryBudget
                    .existsByWalletIdAndUserIdAndCategoryIdAndPeriodAndIdNot(
                            budget.getWallet().getId(),
                            userId,
                            categoryId,
                            period,
                            budget.getId()
                    );
        }

        if (exists) {
            throw new BudgetOverlapException(
                    "A budget already exists for this scope and period"
            );
        }
    }
}
