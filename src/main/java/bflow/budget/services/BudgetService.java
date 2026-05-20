package bflow.budget.services;

import bflow.auth.entities.User;
import bflow.auth.services.UserServiceImpl;
import bflow.budget.DTO.BudgetPatchRequest;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.DTO.BudgetSummaryResponse;
import bflow.budget.RepositoryBudget;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.common.exception.BudgetNotFoundException;
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

    /**
     * Service for notification operations.
     */
    private final NotificationService notificationService;

    private final BudgetValidationService validationService;
    private final BudgetLifecycleService lifecycleService;

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

        Budget budget = getOwnedBudget(budgetId, userId);

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

        //Check the start date for the budget before handle a query
        validationService.validateStartDate(request.getStartDate());

        Budget budget = new Budget();

        budget.setPeriod(request.getPeriod());
        budget.setAmount(request.getAmount());
        budget.setThresholdWarning(request.getThresholdWarning());
        budget.setThresholdCritical(request.getThresholdCritical());
        budget.setStartDate(request.getStartDate());

        Wallet wallet = new Wallet();
        wallet.setId(walletId);

        //If the user sets an wallet that it's not theirs throw exception
        validateWalletAccess(walletId, userId);

        validationService.validateBudgetConstraints(
                request.getScope(),
                request.getCategoryId(),
                request.getThresholdWarning(),
                request.getThresholdCritical()
        );

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

        validateWalletAccess(walletId, userId);

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
            LocalDate end = lifecycleService.calculateEndDate(budget);

            boolean periodEnded = today.isAfter(end);

            if (periodEnded) {
                BudgetResponse endResponse =
                        calculationService.calculate(budget);

                if (endResponse.getStatus() != BudgetStatus.EXCEEDED) {
                    notificationService.sendBudgetSuccess(
                            budget.getUser().getId(),
                            endResponse
                    );
                }

                lifecycleService.resetBudgetPeriod(budget);
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

    /**
     * Get budget summary for a wallet.
     *
     * @param walletId the wallet ID
     * @param userId the user ID
     * @return the budget summary response
     */
    public BudgetSummaryResponse getBudgetSummary(
            final UUID walletId,
            final UUID userId
    ) {

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
                default -> {
                }
            }

            totalBudget = totalBudget.add(b.getBudgetLimit());
            totalSpent = totalSpent.add(b.getSpent());

            if (highest == null
                    || b.getPercentage() > highest.getPercentage()) {
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

    public BudgetResponse patchBudget(
            final UUID budgetId,
            final UUID userId,
            final BudgetPatchRequest request
    ) {

        userService.validateUserActive(userId);

        if (request.getStartDate() != null) {
            validationService.validateStartDate(request.getStartDate());
        }

        Budget budget = getOwnedBudget(budgetId, userId);

        Integer finalWarning =
                request.getThresholdWarning() != null
                        ? request.getThresholdWarning()
                        : budget.getThresholdWarning();

        Integer finalCritical =
                request.getThresholdCritical() != null
                        ? request.getThresholdCritical()
                        : budget.getThresholdCritical();

        BudgetScope finalScope =
                request.getScope() != null
                        ? request.getScope()
                        : budget.getScope();

        UUID finalCategoryId =
                request.getCategoryId() != null
                        ? request.getCategoryId()
                        : budget.getCategoryId();

        if (finalScope == BudgetScope.WALLET) {
            finalCategoryId = null;
        }

        validationService.validateBudgetConstraints(
                finalScope,
                finalCategoryId,
                finalWarning,
                finalCritical
        );

        boolean shouldResetAlerts =
                request.getAmount() != null
                        || request.getPeriod() != null
                        || request.getStartDate() != null
                        || request.getScope() != null
                        || request.getCategoryId() != null;

        if (request.getAmount() != null) {
            budget.setAmount(request.getAmount());
        }

        if (request.getPeriod() != null) {
            budget.setPeriod(request.getPeriod());
        }

        if (request.getStartDate() != null) {
            budget.setStartDate(request.getStartDate());
        }

        budget.setThresholdWarning(finalWarning);
        budget.setThresholdCritical(finalCritical);

        budget.setScope(finalScope);
        budget.setCategoryId(finalCategoryId);

        if (shouldResetAlerts) {
            lifecycleService.resetAlerts(budget);
        }

        Budget updated = repositoryBudget.save(budget);

        return calculationService.calculate(updated);
    }

    public void deleteBudget(
            final UUID budgetId,
            final UUID userId
    ) {

        userService.validateUserActive(userId);

        Budget budget = getOwnedBudget(budgetId, userId);

        repositoryBudget.delete(budget);
    }

    private void validateWalletAccess(
            final UUID walletId,
            final UUID userId
    ) {
        repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );
    }

    private Budget getOwnedBudget(
            final UUID budgetId,
            final UUID userId
    ) {

        return repositoryBudget
                .findByIdAndUserId(budgetId, userId)
                .orElseThrow(() ->
                        new BudgetNotFoundException(
                                "Budget not found"
                        )
                );
    }

}
