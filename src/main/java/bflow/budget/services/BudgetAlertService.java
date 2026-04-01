package bflow.budget.services;

import bflow.budget.DTO.BudgetResponse;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetStatus;
import bflow.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for evaluating budget alerts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public final class BudgetAlertService {
    /**
     * The notification service.
     */
    private final NotificationService notificationService;

    /**
     * Evaluate a budget and send appropriate notifications.
     *
     * @param budgetResponse the budget response
     * @param userId the user ID
     * @param budget the budget entity
     */
    public void evaluate(
            final BudgetResponse budgetResponse,
            final UUID userId,
            final Budget budget
    ) {
        if (budgetResponse == null || budgetResponse.getStatus() == null) {
            return;
        }

        BudgetStatus current = budgetResponse.getStatus();
        BudgetStatus last = budget.getLastAlertStatus();

        if (last == null) {
            last = BudgetStatus.OK;
        }

        if (current == last) {
            return; // no more double warnings
        }

        if (last == BudgetStatus.OK && current == BudgetStatus.CRITICAL) {
            notificationService.sendBudgetCritical(userId, budgetResponse);
        } else if (last == BudgetStatus.OK
                && current == BudgetStatus.EXCEEDED) {
            notificationService.sendBudgetExceeded(userId, budgetResponse);
        } else {
            switch (current) {
                case WARNING ->
                        notificationService.sendBudgetWarning(
                                userId,
                                budgetResponse
                        );

                case CRITICAL ->
                        notificationService.sendBudgetCritical(
                                userId,
                                budgetResponse
                        );

                case EXCEEDED ->
                        notificationService.sendBudgetExceeded(
                                userId,
                                budgetResponse
                        );

                default -> { }
            }
        }

        budget.setLastAlertStatus(current);
    }
}
