package bflow.budget.services;

import bflow.budget.DTO.BudgetResponse;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.expenses.RepositoryExpense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Service for budget calculations.
 */
@Service
@RequiredArgsConstructor
public final class BudgetCalculationService {

    /**
     * Percentage multiplier for decimal conversion.
     */
    private static final int PERCENTAGE_MULTIPLIER = 100;

    /**
     * The expense repository.
     */
    private final RepositoryExpense repositoryExpense;

    /**
     * Service for managing budget lifecycle operations.
     */
    private final BudgetLifecycleService lifecycleService;

    /**
     * Calculate budget response from a budget entity.
     *
     * @param budget the budget entity
     * @return the budget response
     */
    public BudgetResponse calculate(final Budget budget) {

        LocalDate start = budget.getStartDate();
        LocalDate end = lifecycleService.calculateEndDate(budget);

        BigDecimal spent;

        if (budget.getScope() == BudgetScope.WALLET) {
            spent = repositoryExpense.sumExpensesByWalletAndDateRange(
                    budget.getWallet().getId(),
                    start,
                    end
            );
        } else {
            spent = repositoryExpense.sumByCategoryAndDateRange(
                    budget.getCategory() != null ?
                            budget.getCategory().getId() : null,
                    start,
                    end
            );
        }

        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        BigDecimal amount = budget.getAmount();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Budget amount must be greater than 0"
            );
        }

        BigDecimal percentageDecimal = spent
                .multiply(BigDecimal.valueOf(PERCENTAGE_MULTIPLIER))
                .divide(amount, 2, RoundingMode.HALF_UP);

        int percentage = percentageDecimal.intValue();

        BudgetStatus status;

        final int percentageThreshold = 100;
        if (percentage >= percentageThreshold) {
            status = BudgetStatus.EXCEEDED;
        } else if (percentage >= budget.getThresholdCritical()) {
            status = BudgetStatus.CRITICAL;
        } else if (percentage >= budget.getThresholdWarning()) {
            status = BudgetStatus.WARNING;
        } else {
            status = BudgetStatus.OK;
        }

        BudgetResponse response = new BudgetResponse();
        response.setId(budget.getId());
        response.setWalletId(budget.getWallet().getId());
        response.setPeriod(budget.getPeriod());
        response.setStartDate(budget.getStartDate());

        response.setBudgetLimit(budget.getAmount());
        response.setSpent(spent);

        BigDecimal remaining = budget.getAmount().subtract(spent);

        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        response.setRemaining(remaining);

        response.setPercentage(percentage);
        response.setStatus(status);

        response.setThresholdWarning(budget.getThresholdWarning());
        response.setThresholdCritical(budget.getThresholdCritical());

        response.setCreatedAt(budget.getCreatedAt());

        return response;
    }
}
