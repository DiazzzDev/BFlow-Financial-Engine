package bflow.budget.services;

import bflow.budget.DTO.BudgetResponse;
import bflow.budget.enums.BudgetStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BudgetAlertService {
    public void evaluate(BudgetResponse budgetResponse) {
        if (budgetResponse == null || budgetResponse.getStatus() == null) {
            return;
        }

        BudgetStatus status = budgetResponse.getStatus();

        switch (status) {
            case WARNING:
                log.warn("[BUDGET WARNING] Wallet {} in {}%",
                        budgetResponse.getWalletId(),
                        budgetResponse.getPercentage());
                break;

            case CRITICAL:
                log.error("[BUDGET CRITICAL] Wallet {} in {}%",
                        budgetResponse.getWalletId(),
                        budgetResponse.getPercentage());
                break;

            case EXCEEDED:
                log.error("[BUDGET EXCEEDED] Wallet {} exceeded budget ({}%)",
                        budgetResponse.getWalletId(),
                        budgetResponse.getPercentage());
                break;

            default:
                log.debug("[BUDGET OK] Wallet {} in {}%",
                        budgetResponse.getWalletId(),
                        budgetResponse.getPercentage());
        }
    }
}
