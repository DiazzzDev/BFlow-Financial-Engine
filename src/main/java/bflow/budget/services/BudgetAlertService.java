package bflow.budget.services;

import bflow.budget.DTO.BudgetResponse;
import bflow.budget.enums.BudgetStatus;
import bflow.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BudgetAlertService {
    private final NotificationService notificationService;

    public void evaluate(BudgetResponse budgetResponse, UUID userId) {
        if (budgetResponse == null || budgetResponse.getStatus() == null) {
            return;
        }

        switch (budgetResponse.getStatus()) {
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
        }
    }
}
