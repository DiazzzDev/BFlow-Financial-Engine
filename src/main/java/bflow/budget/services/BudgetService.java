package bflow.budget.services;

import bflow.auth.entities.User;
import bflow.auth.services.UserService;
import bflow.budget.DTO.BudgetDetailResponse;
import bflow.budget.DTO.BudgetPatchRequest;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.DTO.BudgetSummaryResponse;

import bflow.budget.DTO.BudgetSearchRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import bflow.budget.DTO.RecentActivityItem;
import bflow.budget.DTO.SpendingTrendPoint;
import bflow.budget.entity.Budget;
import bflow.budget.mapper.BudgetDetailMapper;
import bflow.budget.mapper.BudgetDetailView;
import bflow.budget.mapper.BudgetMapper;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.budget.enums.PeriodType;
import bflow.budget.repository.RepositoryBudget;
import bflow.budget.repository.spec.BudgetSpecification;
import bflow.category.entity.Category;
import bflow.common.exception.BudgetNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.notifications.service.NotificationService;
import bflow.subscription.FeatureCodes;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import org.springframework.data.domain.PageRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class BudgetService {

    /** Generated budget response mapper. */
    private static final BudgetMapper BUDGET_MAPPER =
            Mappers.getMapper(BudgetMapper.class);

    /** Generated mapper for calculated budget detail responses. */
    private static final BudgetDetailMapper BUDGET_DETAIL_MAPPER =
            Mappers.getMapper(BudgetDetailMapper.class);

    /** Number of recent activity items to return. */
    private static final int RECENT_ACTIVITY_LIMIT = 5;

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
    private final UserService userService;

    /**
     * Service for notification operations.
     */
    private final NotificationService notificationService;

    /**
     * Service for validating budget constraints and values.
     */
    private final BudgetValidationService validationService;

    /**
     * Service for managing budget lifecycle operations.
     */
    private final BudgetLifecycleService lifecycleService;

    /**
     * Service for validating budget overlaps with existing budgets.
     */
    private final BudgetOverlapValidationService overlapValidationService;

    /**
     * Service responsible for enforcing subscription plan limits.
     */
    private final PlanLimitService planLimitService;

    /**
     * Repository for expense operations, used to build budget spending
     * trend and recent activity data.
     */
    private final RepositoryExpense repositoryExpense;

    /**
     * Entity manager used for persistence operations.
     */
    private final EntityManager entityManager;

    /**
     * Get the status of a specific budget.
     *
     * @param budgetId the budget ID
     * @param userId the user ID
     * @return the budget response
     */
    @Transactional(readOnly = true)
    public BudgetResponse getBudgetStatus(final UUID budgetId,
                                          final UUID userId) {

        //Check if user has an active account
        userService.validateUserActive(userId);

        Budget budget = getOwnedBudget(budgetId, userId);

        return calculationService.calculate(budget);
    }

    /**
     * Retrieves budgets owned by the authenticated user, applying
     * optional dynamic filters (name, wallet, category, scope, period,
     * status) built via Spring Data Specifications, with pagination
     * and sorting support.
     *
     * <p>Name matching is case-insensitive and normalizes leading/
     * trailing whitespace before comparison, so filters like
     * {@code "Food"}, {@code "food"}, or {@code "  FOOD  "} are
     * treated identically.
     *
     * <p>Ownership restriction is always applied as part of the
     * specification, so results can never include budgets that do
     * not belong to the authenticated user.
     *
     * @param userId the authenticated user's ID
     * @param filter the search criteria (all fields optional)
     * @param pageable pagination and sorting information
     * @return a page of matching budget responses
     */
    @Transactional(readOnly = true)
    public Page<BudgetResponse> getBudgets(
            final UUID userId,
            final BudgetSearchRequest filter,
            final Pageable pageable
    ) {

        userService.validateUserActive(userId);

        Specification<Budget> specification =
                BudgetSpecification.build(filter, userId);

        Page<Budget> budgets =
                repositoryBudget.findAll(specification, pageable);

        return budgets.map(this::toResponse);
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
        userService.validateUserActive(userId);

        validationService.validateAmount(request.getAmount());
        validationService.validateStartDate(request.getStartDate());
        validationService.validateBudgetConstraints(
                request.getScope(),
                request.getWalletId(),
                request.getCategoryId(),
                request.getThresholdWarning(),
                request.getThresholdCritical()
        );

        planLimitService.assertCanCreate(
                userId,
                FeatureCodes.BUDGETS,
                repositoryBudget.countByUserId(userId)
        );

        Budget budget = new Budget();
        budget.setPeriod(request.getPeriod());
        budget.setAmount(request.getAmount());
        budget.setThresholdWarning(request.getThresholdWarning());
        budget.setThresholdCritical(request.getThresholdCritical());
        budget.setStartDate(request.getStartDate());
        budget.setScope(request.getScope());
        budget.setLastAlertStatus(BudgetStatus.OK);

        if (request.getScope() != BudgetScope.CATEGORY_GLOBAL) {
            WalletUser walletUser =
                    requireWalletAccess(request.getWalletId(), userId);
            Currency walletCurrency = walletUser.getWallet().getCurrency();

            validationService.validateCurrency(
                    request.getScope(), request.getCurrency(),
                    walletCurrency
            );

            budget.setCurrency(walletCurrency);
            budget.setWallet(
                    entityManager.getReference(
                            Wallet.class, request.getWalletId()
                    )
            );
        } else {
            validationService.validateCurrency(
                    BudgetScope.CATEGORY_GLOBAL, request.getCurrency(), null
            );
            budget.setCurrency(request.getCurrency());
        }

        overlapValidationService.validateCreateOverlap(request, userId);

        if (request.getCategoryId() != null) {
            Category category = new Category();
            category.setId(request.getCategoryId());
            budget.setCategory(category);
        }

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
    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgetsByWallet(final UUID walletId,
                                                   final UUID userId) {

        //Check if user has an active account
        userService.validateUserActive(userId);

        requireWalletAccess(walletId, userId);

        List<Budget> budgets = repositoryBudget.findByWalletId(walletId);

        return budgets.stream()
                .map(calculationService::calculate)
                .toList();
    }

    /**
     * Get budget summary for a wallet.
     *
     * @param walletId the wallet ID
     * @param userId the user ID
     * @return the budget summary response
     */
    @Transactional(readOnly = true)
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

    /**
     * Apply partial updates to an existing budget.
     *
     * @param budgetId the ID of the budget to update
     * @param userId the ID of the user (owner)
     * @param request the patch request containing updated fields
     * @return the updated budget response
     */
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

        BudgetScope finalScope = request.getScope() != null
                        ? request.getScope()
                        : budget.getScope();

        UUID currentCategoryId = budget.getCategory() != null
                        ? budget.getCategory().getId()
                        : null;

        UUID finalCategoryId = request.getCategoryId() != null
                        ? request.getCategoryId()
                        : currentCategoryId;

        UUID currentWalletId = budget.getWallet() != null
                        ? budget.getWallet().getId()
                        : null;

        UUID finalWalletId =
                request.getWalletId() != null
                        ? request.getWalletId()
                        : currentWalletId;

        if (finalScope == BudgetScope.WALLET) {
            finalCategoryId = null;
        }

        if (finalScope == BudgetScope.CATEGORY_GLOBAL) {
            finalWalletId = null;
        }

        validationService.validateBudgetConstraints(
                finalScope,
                finalWalletId,
                finalCategoryId,
                finalWarning,
                finalCritical
        );

        PeriodType finalPeriod = request.getPeriod() != null
                ? request.getPeriod()
                : budget.getPeriod();

        overlapValidationService.validatePatchOverlap(
                budget,
                finalScope,
                finalWalletId,
                finalCategoryId,
                finalPeriod,
                userId
        );

        boolean shouldResetAlerts =
                request.getAmount() != null
                        || request.getPeriod() != null
                        || request.getStartDate() != null
                        || request.getScope() != null
                        || request.getCategoryId() != null
                        || request.getWalletId() != null
                        || request.getCurrency() != null;

        if (request.getAmount() != null) {
            validationService.validateAmount(request.getAmount());
            budget.setAmount(request.getAmount());
        }

        budget.setPeriod(finalPeriod);

        if (request.getStartDate() != null) {
            budget.setStartDate(request.getStartDate());
        }

        budget.setThresholdWarning(finalWarning);
        budget.setThresholdCritical(finalCritical);
        budget.setScope(finalScope);

        Currency finalCurrency;

        if (finalWalletId != null) {
            // Access is re-verified every time, not only when the
            // wallet actually changes — this is what protects
            // against a user who lost access to the wallet after
            // the budget was originally created.
            WalletUser walletUser =
                    requireWalletAccess(finalWalletId, userId);
            Currency walletCurrency = walletUser.getWallet().getCurrency();

            // The wallet's currency is always the source of truth
            // for WALLET/WALLET_CATEGORY scope. An explicit currency
            // on the patch is only used to CATCH a contradiction
            // (client sending a currency that doesn't match the
            // wallet); it never overrides the wallet's real value.
            if (request.getCurrency() != null) {
                validationService.validateCurrency(
                        finalScope, request.getCurrency(), walletCurrency
                );
            }
            finalCurrency = walletCurrency;
            budget.setWallet(
                entityManager.getReference(Wallet.class, finalWalletId)
            );
        } else {
            // CATEGORY_GLOBAL: an explicit currency on the patch
            // overrides the budget's existing one; otherwise the
            // existing currency carries over unchanged rather than
            // being wiped out by a patch that never mentioned it.
            finalCurrency = request.getCurrency() != null
                    ? request.getCurrency()
                    : budget.getCurrency();

            validationService.validateCurrency(
                    BudgetScope.CATEGORY_GLOBAL, finalCurrency, null
            );
            budget.setWallet(null);
        }

        budget.setCurrency(finalCurrency);

        if (finalCategoryId != null) {
            Category category = new Category();
            category.setId(finalCategoryId);
            budget.setCategory(category);
        } else {
            budget.setCategory(null);
        }

        if (shouldResetAlerts) {
            lifecycleService.resetAlerts(budget);
        }

        Budget updated = repositoryBudget.save(budget);
        return calculationService.calculate(updated);
    }

    /**
     * Delete a budget by ID.
     *
     * @param budgetId the ID of the budget to delete
     * @param userId the ID of the user (owner)
     */
    public void deleteBudget(
            final UUID budgetId,
            final UUID userId
    ) {

        userService.validateUserActive(userId);

        Budget budget = getOwnedBudget(budgetId, userId);

        repositoryBudget.delete(budget);
    }

    /**
     * Get the full detail view (overview, trend, recent activity) for a
     * single budget, used by the budget dashboard UI.
     *
     * @param budgetId the budget ID
     * @param userId the user ID
     * @return the aggregated budget detail response
     */
    @Transactional(readOnly = true)
    public BudgetDetailResponse getBudgetDetail(
            final UUID budgetId,
            final UUID userId
    ) {
        userService.validateUserActive(userId);

        Budget budget = getOwnedBudget(budgetId, userId);
        BudgetResponse base = calculationService.calculate(budget);

        LocalDate today = LocalDate.now();
        LocalDate start = budget.getStartDate();
        LocalDate end = lifecycleService.calculateEndDate(budget);

        // Bound the "active window" so future-dated budgets don't blow up
        // the day count, and so we never look past the period's end date.
        LocalDate rangeEnd = today.isBefore(start) ? start
                : (today.isAfter(end) ? end : today);

        int daysLeft = (int) Math.max(0, ChronoUnit.DAYS.between(today, end));
        int daysElapsed = (int) (ChronoUnit.DAYS.between(start, rangeEnd) + 1);

        List<Expense> periodExpenses =
                findExpensesInRange(budget, start, rangeEnd);

        BigDecimal avgDaily = daysElapsed > 0
                ? base.getSpent().divide(
                BigDecimal.valueOf(daysElapsed),
                2,
                java.math.RoundingMode.HALF_UP
        )
                : null;
        BigDecimal projectedTotal = null;
        if (avgDaily != null) {
            long totalPeriodDays = ChronoUnit.DAYS.between(start, end) + 1;
            projectedTotal = avgDaily.multiply(
                    BigDecimal.valueOf(totalPeriodDays)
            );
        }

        Currency currency = budget.getWallet() == null
                ? null : budget.getWallet().getCurrency();

        BudgetDetailView detailView = new BudgetDetailView(
                budget,
                currency,
                base.getStatus(),
                start,
                end,
                daysLeft,
                daysElapsed,
                base.getBudgetLimit(),
                base.getSpent(),
                base.getBudgetLimit().subtract(base.getSpent()),
                base.getPercentage(),
                budget.getThresholdWarning(),
                budget.getThresholdCritical(),
                periodExpenses.size(),
                avgDaily,
                projectedTotal,
                buildSpendingTrend(periodExpenses, start, rangeEnd),
                buildRecentActivity(budget, start, rangeEnd)
        );

        return BUDGET_DETAIL_MAPPER.toResponse(detailView);
    }

    /**
     * Builds the cumulative spending trend for the current budget period.
     *
     * @param expenses expenses included in the current budget period
     * @param start the period start date
     * @param rangeEnd the end of the period to include in the trend
     * @return list of trend points
     */
    private List<SpendingTrendPoint> buildSpendingTrend(
            final List<Expense> expenses,
            final LocalDate start,
            final LocalDate rangeEnd
    ) {
        Map<LocalDate, BigDecimal> dailyTotals = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getDate,
                        LinkedHashMap::new,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Expense::getAmount,
                                BigDecimal::add
                        )
                ));

        long totalDays = ChronoUnit.DAYS.between(start, rangeEnd) + 1;

        List<SpendingTrendPoint> trend = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;

        for (int i = 0; i < totalDays; i++) {
            LocalDate date = start.plusDays(i);
            cumulative = cumulative.add(
                    dailyTotals.getOrDefault(date, BigDecimal.ZERO)
            );
            trend.add(new SpendingTrendPoint(i + 1, date, cumulative));
        }

        return trend;
    }

    /**
     * Builds the recent activity list (last N transactions within the
     * budget's current period) for the budget.
     *
     * @param budget the budget
     * @param start the period start date
     * @param rangeEnd the end of the range to search (today, capped to
     *         the period start for future-dated budgets)
     * @return list of recent activity items
     */
    private List<RecentActivityItem> buildRecentActivity(
            final Budget budget,
            final LocalDate start,
            final LocalDate rangeEnd
    ) {
        return findRecentExpenses(
                budget, start, rangeEnd, RECENT_ACTIVITY_LIMIT
        )
                .stream()
                .map(e -> new RecentActivityItem(
                        e.getId(),
                        e.getTitle(),
                        e.getDate(),
                        e.getAmount()
                ))
                .toList();
    }

    /**
     * Finds all expenses for a budget's wallet (and category, if scoped)
     * within a date range, ordered chronologically.
     *
     * @param budget the budget
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @return matching expenses
     */
    private List<Expense> findExpensesInRange(
            final Budget budget,
            final LocalDate start,
            final LocalDate end
    ) {
        if (budget.getScope() == BudgetScope.CATEGORY_GLOBAL) {
            List<UUID> walletIds =
                    repositoryWalletUser.findWalletIdsByUserId(
                            budget.getUser().getId()
                    );
            return repositoryExpense
                    .findByWalletIdInAndCategoryIdAndDateBetweenOrderByDateAsc(
                            walletIds, budget.getCategory().getId(), start, end
                    );
        }

        boolean scopedToCategory =
                budget.getScope() == BudgetScope.WALLET_CATEGORY
                        && budget.getCategory() != null;

        if (scopedToCategory) {
            return repositoryExpense
                    .findByWalletIdAndCategoryIdAndDateBetweenOrderByDateAsc(
                            budget.getWallet().getId(),
                            budget.getCategory().getId(),
                            start,
                            end
                    );
        }

        return repositoryExpense.findByWalletIdAndDateBetweenOrderByDateAsc(
                budget.getWallet().getId(),
                start,
                end
        );
    }

    /**
     * Finds the most recent expenses for a budget's wallet (and category,
     * if scoped) within the given date range.
     *
     * @param budget the budget
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @param limit max number of results
     * @return matching expenses ordered by date descending
     */
    private List<Expense> findRecentExpenses(
            final Budget budget,
            final LocalDate start,
            final LocalDate end,
            final int limit
    ) {
        Pageable pageable = PageRequest.of(0, limit);

        if (budget.getScope() == BudgetScope.CATEGORY_GLOBAL) {
            List<UUID> walletIds =
                    repositoryWalletUser.findWalletIdsByUserId(
                            budget.getUser().getId()
                    );
            return repositoryExpense
       .findByWalletIdInAndCategoryIdAndDateBetweenOrderByDateDescCreatedAtDesc(
                walletIds,
                budget.getCategory().getId(),
                start,
                end,
                pageable
            );
        }

        boolean scopedToCategory =
                budget.getScope() == BudgetScope.WALLET_CATEGORY
                        && budget.getCategory() != null;

        if (scopedToCategory) {
            return repositoryExpense
        .findByWalletIdAndCategoryIdAndDateBetweenOrderByDateDescCreatedAtDesc(
                            budget.getWallet().getId(),
                            budget.getCategory().getId(),
                            start,
                            end,
                            pageable
                    );
        }

        return repositoryExpense
                .findByWalletIdAndDateBetweenOrderByDateDescCreatedAtDesc(
                        budget.getWallet().getId(),
                        start,
                        end,
                        pageable
                );
    }

    private WalletUser requireWalletAccess(
            final UUID walletId,
            final UUID userId
    ) {
        return repositoryWalletUser
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

    /**
     * Parse entity to response.
     *
     * @param budget the entity of the budget to parse
     * @return the dto response
     */
    public BudgetResponse toResponse(final Budget budget) {
        return BUDGET_MAPPER.toResponse(budget);
    }

    /**
     * Evaluates every budget affected by an expense event: budgets scoped
     * to the wallet directly, plus CATEGORY_GLOBAL budgets belonging to
     * any user who participates in that wallet, for the given category.
     *
     * @param walletId the wallet where the expense occurred
     * @param categoryId the category of the expense, or {@code null} if
     *         the expense has no category (only WALLET-scoped budgets
     *         will be affected in that case)
     */
    public void evaluateBudgetsForExpenseEvent(
            final UUID walletId,
            final UUID categoryId
    ) {
        evaluateBudgetsForWallet(walletId);

        if (categoryId == null) {
            return;
        }

        List<UUID> memberIds = repositoryWalletUser.findByWalletId(walletId)
                .stream()
                .map(wu -> wu.getUser().getId())
                .distinct()
                .toList();

        for (UUID memberId : memberIds) {
            List<Budget> globalBudgets = repositoryBudget
                    .findByUserIdAndScopeAndCategoryId(
                            memberId, BudgetScope.CATEGORY_GLOBAL, categoryId
                    );

            for (Budget budget : globalBudgets) {
                evaluateOne(budget);
            }
        }
    }

    /**
     * Evaluates a single budget: checks whether its period ended (and
     * resets it), or whether it's active and needs an alert.
     *
     * @param budget the budget to evaluate
     */
    private void evaluateOne(final Budget budget) {
        LocalDate today = LocalDate.now();
        LocalDate start = budget.getStartDate();
        LocalDate end = lifecycleService.calculateEndDate(budget);

        boolean periodEnded = today.isAfter(end);

        if (periodEnded) {
            BudgetResponse endResponse = calculationService.calculate(budget);

            if (endResponse.getStatus() != BudgetStatus.EXCEEDED) {
                notifySuccess(budget, endResponse);
            }

            lifecycleService.resetBudgetPeriod(budget);
            repositoryBudget.save(budget);
            return;
        }

        boolean isActive =
                (today.isEqual(start) || today.isAfter(start))
                        && today.isBefore(end);

        if (!isActive) {
            return;
        }

        BudgetResponse response = calculationService.calculate(budget);

        alertService.evaluate(response, budget.getUser().getId(), budget);

        repositoryBudget.save(budget);
    }

    /**
     * Notifies about a completed, non-exceeded budget period. If the
     * budget is tied to a shared wallet (scope WALLET or
     * WALLET_CATEGORY) with more than one member, every member gets
     * a team celebration instead of just the budget's owner —
     * turning "stayed on budget" into a shared win rather than a
     * private one. CATEGORY_GLOBAL budgets and single-member wallets
     * keep the existing personal notification.
     *
     * @param budget the budget whose period just ended
     * @param response the final calculated figures for the period
     */
    private void notifySuccess(
            final Budget budget,
            final BudgetResponse response
    ) {
        if (budget.getWallet() != null) {
            List<WalletUser> walletMembers = repositoryWalletUser
                    .findByWalletId(budget.getWallet().getId());

            if (walletMembers.size() > 1) {
                List<User> members = walletMembers.stream()
                        .map(WalletUser::getUser)
                        .distinct()
                        .toList();

                notificationService.sendBudgetGroupSuccess(
                        members, budget.getWallet().getName(), response
                );
                return;
            }
        }

        notificationService.sendBudgetSuccess(
                budget.getUser().getId(), response
        );
    }

    /**
     * Evaluate all wallet-scoped and wallet-category budgets for a wallet.
     *
     * @param walletId the wallet ID
     */
    public void evaluateBudgetsForWallet(final UUID walletId) {
        List<Budget> budgets = repositoryBudget.findByWalletId(walletId);

        for (Budget budget : budgets) {
            evaluateOne(budget);
        }
    }
}
