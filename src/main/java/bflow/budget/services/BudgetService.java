package bflow.budget.services;

import bflow.auth.entities.User;
import bflow.auth.services.UserServiceImpl;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.DTO.BudgetSummaryResponse;
import bflow.budget.RepositoryBudget;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.notifications.service.NotificationService;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.entities.Wallet;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class BudgetService {
    /**
     * Repository for budget operations.
     */
    private final RepositoryBudget repositoryBudget;
    /**
     * Service for budget calculations.
     */
    private final BudgetCalculationService calculationService;
    /**
     * Service for budget alerts.
     */
    private final BudgetAlertService alertService;
    /**
     * Repository for wallet user operations.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Service for user business logic operations.
     */
    private final UserServiceImpl userService;

    private final NotificationService notificationService;

    /**
     * Get the status of a specific budget.
     *
     * @param budgetId the budget ID
     * @param userId the user ID
     * @return the budget response
     */
    public BudgetResponse getBudgetStatus(final UUID budgetId,
            final UUID userId) {

        //Check if user has an active account
        userService.validateUserActive(userId);

        Budget budget = repositoryBudget.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        if (!budget.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException(
                    "You do not have access to this budget"
            );
        }

        return calculationService.calculate(budget);
    }

    /**
     * Create a new budget.
     *
     * @param request the budget request
     * @param userId the user ID
     * @param walletId the wallet ID
     * @return the created budget response
     */
    public BudgetResponse createBudget(
            final BudgetRequest request,
            final UUID userId,
            final UUID walletId
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

        if (request.getScope() == BudgetScope.CATEGORY
                && request.getCategoryId() == null) {
            throw new IllegalArgumentException("Category budget requires categoryId");
        }

        budget.setWallet(wallet);
        budget.setScope(request.getScope());
        budget.setCategoryId(request.getCategoryId());

        User user = new User();
        user.setId(userId);
        budget.setUser(user);

        Budget saved = repositoryBudget.saveAndFlush(budget);

        return calculationService.calculate(saved);
    }

    /**
     * Get all budgets for a specific wallet.
     *
     * @param walletId the wallet ID
     * @param userId the user ID
     * @return list of budget responses
     */
    public List<BudgetResponse> getBudgetsByWallet(final UUID walletId,
            final UUID userId) {

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

    /**
     * Evaluate all budgets for a wallet.
     *
     * @param walletId the wallet ID
     */
    public void evaluateBudgetsForWallet(final UUID walletId) {

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

            boolean periodEnded = today.isAfter(end);

            if (periodEnded) {

                BudgetResponse endResponse = calculationService.calculate(budget);

                if (endResponse.getStatus() != BudgetStatus.EXCEEDED) {
                    notificationService.sendBudgetSuccess(
                            budget.getUser().getId(),
                            endResponse
                    );
                }

                resetBudgetPeriod(budget);
                repositoryBudget.save(budget);

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
                    budget.getUser().getId(),
                    budget
            );

            repositoryBudget.save(budget);
        }
    }

    private void resetBudgetPeriod(Budget budget) {

        LocalDate newStart = LocalDate.now();

        budget.setStartDate(newStart);
        budget.setLastAlertStatus(BudgetStatus.OK);
    }

    public BudgetSummaryResponse getBudgetSummary(
            final UUID walletId,
            final UUID userId
    ) {

        userService.validateUserActive(userId);

        repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );

        List<BudgetResponse> budgets =
                getBudgetsByWallet(walletId, userId);

        BudgetSummaryResponse summary = new BudgetSummaryResponse();

        summary.setTotal(budgets.size());

        int ok = 0;
        int warning = 0;
        int critical = 0;
        int exceeded = 0;

        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;

        BudgetResponse highest = null;

        for (BudgetResponse b : budgets) {

            switch (b.getStatus()) {
                case OK -> ok++;
                case WARNING -> warning++;
                case CRITICAL -> critical++;
                case EXCEEDED -> exceeded++;
            }

            totalBudget = totalBudget.add(b.getBudgetLimit());
            totalSpent = totalSpent.add(b.getSpent());

            if (highest == null ||
                    b.getPercentage() > highest.getPercentage()) {
                highest = b;
            }
        }

        summary.setOk(ok);
        summary.setWarning(warning);
        summary.setCritical(critical);
        summary.setExceeded(exceeded);

        summary.setTotalBudget(totalBudget);
        summary.setTotalSpent(totalSpent);
        summary.setTotalRemaining(
                totalBudget.subtract(totalSpent)
        );

        summary.setHighestUsage(highest);

        return summary;
    }
}
