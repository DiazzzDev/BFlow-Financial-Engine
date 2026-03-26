package bflow.budget.services;

import bflow.budget.DTO.BudgetResponse;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetStatus;
import bflow.expenses.RepositoryExpense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BudgetCalculationService {

    private final RepositoryExpense repositoryExpense;

    public BudgetResponse calculate(Budget budget) {

        LocalDate start = budget.getStartDate();
        LocalDate end;

        switch (budget.getPeriod()) {
            case WEEKLY:
                end = start.plusWeeks(1);
                break;
            case MONTHLY:
                end = start.plusMonths(1);
                break;
            case DAILY:
                end = start.plusDays(1);
                break;
            default:
                throw new RuntimeException("Invalid period");
        }

        BigDecimal spent = repositoryExpense.sumExpensesByWalletAndDateRange(
                budget.getWallet().getId(),
                start,
                end
        );

        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        BigDecimal percentageDecimal = spent
                .multiply(BigDecimal.valueOf(100))
                .divide(budget.getAmount(), 2, RoundingMode.HALF_UP);

        int percentage = percentageDecimal.intValue();

        BudgetStatus status;

        if (percentage >= 100) {
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
        response.setRemaining(budget.getAmount().subtract(spent));
        response.setPercentage(percentage);
        response.setStatus(status);

        response.setThresholdWarning(budget.getThresholdWarning());
        response.setThresholdCritical(budget.getThresholdCritical());

        response.setCreatedAt(budget.getCreatedAt());

        return response;
    }
}