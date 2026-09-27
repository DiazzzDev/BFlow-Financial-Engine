package Diaz.Dev.BFlow.budget.services;

import bflow.budget.entity.Budget;
import bflow.budget.enums.PeriodType;
import bflow.budget.services.BudgetLifecycleService;
import bflow.common.i18n.MessageService;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the period boundaries calculated for a budget.
 */
class BudgetLifecycleServiceTest {

    @Test
    void calculateEndDate_yearly_advancesOneCalendarYear() {
        Budget budget = new Budget();
        budget.setPeriod(PeriodType.YEARLY);
        budget.setStartDate(LocalDate.of(2024, 2, 29));

        BudgetLifecycleService service = new BudgetLifecycleService(
                mock(MessageService.class)
        );

        assertEquals(
                LocalDate.of(2025, 2, 28),
                service.calculateEndDate(budget)
        );
    }
}
