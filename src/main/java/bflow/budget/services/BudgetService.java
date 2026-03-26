package bflow.budget.services;

import bflow.auth.entities.User;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.RepositoryBudget;
import bflow.budget.entity.Budget;
import bflow.wallet.entities.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetService  {
    private final RepositoryBudget repositoryBudget;
    private final BudgetCalculationService calculationService;
    private final BudgetAlertService alertService;

    public BudgetResponse getBudgetStatus(UUID budgetId) {

        Budget budget = repositoryBudget.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        BudgetResponse response = calculationService.calculate(budget);

        alertService.evaluate(response);

        return response;
    }

    public BudgetResponse createBudget(
            BudgetRequest request,
            UUID userId,
            UUID walletId
    ) {

        Budget budget = new Budget();

        budget.setPeriod(request.getPeriod());
        budget.setAmount(request.getAmount());
        budget.setThresholdWarning(request.getThresholdWarning());
        budget.setThresholdCritical(request.getThresholdCritical());
        budget.setStartDate(request.getStartDate());

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        budget.setWallet(wallet);

        User user = new User();
        user.setId(userId);
        budget.setUser(user);

        Budget saved = repositoryBudget.save(budget);

        return calculationService.calculate(saved);
    }

    public List<BudgetResponse> getBudgetsByWallet(UUID walletId) {

        List<Budget> budgets = repositoryBudget.findByWalletId(walletId);

        return budgets.stream()
                .map(calculationService::calculate)
                .toList();
    }

    private BudgetResponse mapToResponse(Budget budget) {

        BudgetResponse response = new BudgetResponse();

        response.setId(budget.getId());
        response.setWalletId(budget.getWallet().getId());
        response.setPeriod(budget.getPeriod());
        response.setStartDate(budget.getStartDate());

        response.setBudgetLimit(budget.getAmount());
        response.setThresholdWarning(budget.getThresholdWarning());
        response.setThresholdCritical(budget.getThresholdCritical());

        response.setCreatedAt(budget.getCreatedAt());

        return response;
    }

    public void evaluateBudgetsForWallet(UUID walletId) {

        List<Budget> budgets = repositoryBudget.findByWalletId(walletId);

        for (Budget budget : budgets) {

            BudgetResponse response =
                    calculationService.calculate(budget);

            alertService.evaluate(response);
        }
    }
}
