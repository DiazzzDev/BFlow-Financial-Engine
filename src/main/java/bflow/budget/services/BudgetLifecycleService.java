package bflow.budget.services;

import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetStatus;
import bflow.common.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class BudgetLifecycleService {

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Calculate the end date of a budget based on its period type.
     *
     * @param budget the budget entity
     * @return the calculated end date
     */
    public LocalDate calculateEndDate(final Budget budget) {

        return switch (budget.getPeriod()) {
            case DAILY -> budget.getStartDate().plusDays(1);
            case WEEKLY -> budget.getStartDate().plusWeeks(1);
            case MONTHLY -> budget.getStartDate().plusMonths(1);
            default -> throw new IllegalStateException(
                    messageService.get("budget.period.unsupported")
            );
        };
    }

    /**
     * Reset alert status for a budget back to OK.
     *
     * @param budget the budget entity to update
     */
    public void resetAlerts(final Budget budget) {
        budget.setLastAlertStatus(BudgetStatus.OK);
    }

    /**
     * Reset the budget period to start from today and reset its alerts.
     *
     * @param budget the budget entity to update
     */
    public void resetBudgetPeriod(final Budget budget) {

        budget.setStartDate(LocalDate.now());
        resetAlerts(budget);
    }
}
