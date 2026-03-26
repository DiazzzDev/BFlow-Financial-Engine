package bflow.budget.services;

import bflow.auth.entities.User;
import bflow.auth.services.UserServiceImpl;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.RepositoryBudget;
import bflow.budget.entity.Budget;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.entities.Wallet;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class BudgetService  {
    private final RepositoryBudget repositoryBudget;
    private final BudgetCalculationService calculationService;
    private final BudgetAlertService alertService;
    private final RepositoryWalletUser repositoryWalletUser;

    /** Service for user business logic operations. */
    private final UserServiceImpl userService;

    public BudgetResponse getBudgetStatus(UUID budgetId, UUID userId) {

        //Check if user has an active account
        userService.validateUserActive(userId);

        Budget budget = repositoryBudget.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        if (!budget.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException(
                    "You do not have access to this budget"
            );
        }

        BudgetResponse response = calculationService.calculate(budget);

        alertService.evaluate(response, userId);

        return response;
    }

    public BudgetResponse createBudget(
            BudgetRequest request,
            UUID userId,
            UUID walletId
    ) {

        //Check if user has an active account
        userService.validateUserActive(userId);

        Budget budget = new Budget();

        budget.setPeriod(request.getPeriod());
        budget.setAmount(request.getAmount());
        budget.setThresholdWarning(request.getThresholdWarning());
        budget.setThresholdCritical(request.getThresholdCritical());
        budget.setStartDate(request.getStartDate());

        Wallet wallet = new Wallet();
        wallet.setId(walletId);

        //If the user sets an wallet that it's not theirs throw exception
        repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );

        budget.setWallet(wallet);

        User user = new User();
        user.setId(userId);
        budget.setUser(user);

        Budget saved = repositoryBudget.saveAndFlush(budget);

        return calculationService.calculate(saved);
    }

    public List<BudgetResponse> getBudgetsByWallet(UUID walletId, UUID userId) {

        //Check if user has an active account
        userService.validateUserActive(userId);

        repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );

        List<Budget> budgets = repositoryBudget.findByWalletId(walletId);

        return budgets.stream()
                .map(calculationService::calculate)
                .toList();
    }

    public void evaluateBudgetsForWallet(UUID walletId) {

        List<Budget> budgets = repositoryBudget.findByWalletId(walletId);

        LocalDate today = LocalDate.now();

        for (Budget budget : budgets) {
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
                    continue;
            }

            boolean isActive =
                    (today.isEqual(start) || today.isAfter(start))
                            && today.isBefore(end);

            if (!isActive) {
                continue;
            }

            BudgetResponse response =
                    calculationService.calculate(budget);

            alertService.evaluate(
                    response,
                    budget.getUser().getId());
        }
    }
}
