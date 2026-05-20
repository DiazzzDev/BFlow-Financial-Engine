package bflow.budget.services;

import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BudgetLifecycleService {

    public LocalDate calculateEndDate(final Budget budget) {

        return switch (budget.getPeriod()) {
            case DAILY -> budget.getStartDate().plusDays(1);
            case WEEKLY -> budget.getStartDate().plusWeeks(1);
            case MONTHLY -> budget.getStartDate().plusMonths(1);
            default -> throw new IllegalStateException(
                    "Unsupported budget period"
            );
        };
    }

    public void resetAlerts(final Budget budget) {
        budget.setLastAlertStatus(BudgetStatus.OK);
    }

    public void resetBudgetPeriod(final Budget budget) {

        budget.setStartDate(LocalDate.now());
        resetAlerts(budget);
    }
}