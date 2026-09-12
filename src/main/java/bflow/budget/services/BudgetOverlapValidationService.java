package bflow.budget.services;

import bflow.budget.DTO.BudgetRequest;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.PeriodType;
import bflow.budget.repository.RepositoryBudget;
import bflow.common.exception.BudgetOverlapException;
import bflow.common.i18n.MessageService;
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

    /** Service for resolving localized messages. */
    private final MessageService messageService;

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
        boolean exists = switch (request.getScope()) {
            case WALLET -> repositoryBudget
                    .existsByWalletIdAndUserIdAndScopeAndPeriod(
                            request.getWalletId(), userId,
                            request.getScope(), request.getPeriod()
                    );
            case WALLET_CATEGORY -> repositoryBudget
                    .existsByWalletIdAndUserIdAndCategoryIdAndPeriod(
                            request.getWalletId(), userId,
                            request.getCategoryId(), request.getPeriod()
                    );
            case CATEGORY_GLOBAL -> repositoryBudget
                    .existsByUserIdAndScopeAndCategoryIdAndPeriod(
                            userId, request.getScope(),
                            request.getCategoryId(), request.getPeriod()
                    );
        };

        if (exists) {
            throw new BudgetOverlapException(
                    messageService.get("budget.overlap")
            );
        }
    }

    /**
     * Validate that patching a budget does not create overlap with other
     * budgets.
     *
     * @param budget the budget entity being updated
     * @param scope the new budget scope
     * @param walletId the wallet ID
     * @param categoryId the new category ID
     * @param period the new period type
     * @param userId the ID of the user (owner)
     * @throws BudgetOverlapException if update creates an overlap
     */
    public void validatePatchOverlap(
            final Budget budget,
            final BudgetScope scope,
            final UUID walletId,
            final UUID categoryId,
            final PeriodType period,
            final UUID userId
    ) {
        boolean exists = switch (scope) {
            case WALLET -> repositoryBudget
                    .existsByWalletIdAndUserIdAndScopeAndPeriodAndIdNot(
                            walletId, userId, scope, period, budget.getId()
                    );
            case WALLET_CATEGORY -> repositoryBudget
                    .existsByWalletIdAndUserIdAndCategoryIdAndPeriodAndIdNot(
                            walletId, userId, categoryId, period, budget.getId()
                    );
            case CATEGORY_GLOBAL -> repositoryBudget
                    .existsByUserIdAndScopeAndCategoryIdAndPeriodAndIdNot(
                            userId, scope, categoryId, period, budget.getId()
                    );
        };

        if (exists) {
            throw new BudgetOverlapException(
                    messageService.get("budget.overlap")
            );
        }
    }
}
