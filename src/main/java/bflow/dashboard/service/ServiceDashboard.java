package bflow.dashboard.service;

import bflow.auth.services.UserService;
import bflow.budget.entity.Budget;
import bflow.budget.repository.RepositoryBudget;
import bflow.dashboard.dto.ActivityBreakdownResponse;
import bflow.dashboard.dto.AveragesResponse;
import bflow.dashboard.dto.BalanceSummaryResponse;
import bflow.dashboard.dto.BudgetHealthItem;
import bflow.dashboard.dto.CategoryPercentage;
import bflow.dashboard.dto.MonthlyPoint;
import bflow.dashboard.dto.RecentActivityItem;
import bflow.dashboard.dto.SpendingSummaryResponse;
import bflow.dashboard.dto.StatisticsResponse;
import bflow.dashboard.projection.CategorySpendingProjection;
import bflow.dashboard.projection.MonthlyTotalProjection;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.income.entity.Income;
import bflow.tranfers.RepositoryTransfers;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregates data from wallet, expense and income repositories to build
 * the widgets shown on the user's main dashboard.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceDashboard {

    /** Number of items shown in the recent activity widget. */
    private static final int RECENT_ACTIVITY_LIMIT = 5;

    /** Number of top categories shown in the spending widget. */
    private static final int TOP_CATEGORIES_LIMIT = 3;

    /** Scale used for percentage calculations before rounding. */
    private static final int PERCENTAGE_SCALE = 4;

    /** Final rounding scale for percentages returned to the client. */
    private static final int PERCENTAGE_DISPLAY_SCALE = 1;

    /** Multiplier used to convert a ratio into a percentage. */
    private static final BigDecimal PERCENTAGE_MULTIPLIER =
            BigDecimal.valueOf(100);

    /** Percentage value returned when a metric grows from a zero base. */
    private static final double NEW_ACTIVITY_PERCENTAGE = 100.0;

    /** First calendar month. */
    private static final int JANUARY = 1;

    /** Last calendar month. */
    private static final int DECEMBER = 12;

    /** Last day of December, used to build a full-year date range. */
    private static final int DECEMBER_LAST_DAY = 31;

    /** Repository for wallet-user membership queries. */
    private final RepositoryWalletUser repositoryWalletUser;

    /** Repository for wallet balance queries. */
    private final RepositoryWallet repositoryWallet;

    /** Repository for expense aggregation queries. */
    private final RepositoryExpense repositoryExpense;

    /** Repository for income aggregation queries. */
    private final RepositoryIncome repositoryIncome;

    /** Repository for budget health queries. */
    private final RepositoryBudget repositoryBudget;

    /** Service for user account validation. */
    private final UserService userService;

    /** Repository for transfer aggregation queries. */
    private final RepositoryTransfers repositoryTransfer;

    /**
     * Builds the "Balance total" widget: current balance across every
     * wallet the user belongs to, plus percentage change vs the balance
     * at the start of the current month.
     *
     * Assumes balance only moves via income/expense at the aggregate
     * level (transfers between the user's own wallets cancel out when
     * summed together).
     *
     * @param userId the authenticated user's ID.
     * @return the balance summary.
     */
    public BalanceSummaryResponse getBalanceSummary(final UUID userId) {
        userService.validateUserActive(userId);
        List<UUID> walletIds = repositoryWalletUser
                .findWalletIdsByUserId(userId);

        if (walletIds.isEmpty()) {
            return new BalanceSummaryResponse(BigDecimal.ZERO, 0.0);
        }

        BigDecimal currentBalance = repositoryWallet
                .sumBalanceByWalletIds(walletIds);

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);

        BigDecimal incomeThisMonth = repositoryIncome
                .sumByWalletsAndDateRange(walletIds, startOfMonth, today);
        BigDecimal expenseThisMonth = repositoryExpense
                .sumByWalletsAndDateRange(walletIds, startOfMonth, today);

        BigDecimal balanceStartOfMonth = currentBalance
                .subtract(incomeThisMonth)
                .add(expenseThisMonth);

        Double percentageChange = calculatePercentageChange(
                balanceStartOfMonth, currentBalance
        );

        return new BalanceSummaryResponse(currentBalance, percentageChange);
    }

    /**
     * Builds the "Statistics" widget: total income vs total expenses per
     * month for the given year (defaults to the current year).
     *
     * @param userId the authenticated user's ID.
     * @param year the target year, or {@code null} for the current year.
     * @return the monthly income/expense series, Jan through Dec.
     */
    public StatisticsResponse getStatistics(
            final UUID userId, final Integer year
    ) {
        userService.validateUserActive(userId);
        List<UUID> walletIds = repositoryWalletUser
                .findWalletIdsByUserId(userId);

        int targetYear = year != null ? year : LocalDate.now().getYear();
        LocalDate start = LocalDate.of(targetYear, JANUARY, 1);
        LocalDate end = LocalDate.of(targetYear, DECEMBER, DECEMBER_LAST_DAY);

        Map<Integer, BigDecimal> incomeByMonth = walletIds.isEmpty()
                ? Map.of()
                : toMonthMap(repositoryIncome
                .sumGroupedByMonth(walletIds, start, end));

        Map<Integer, BigDecimal> expenseByMonth = walletIds.isEmpty()
                ? Map.of()
                : toMonthMap(repositoryExpense
                .sumGroupedByMonth(walletIds, start, end));

        List<MonthlyPoint> points = new ArrayList<>();
        for (int month = JANUARY; month <= DECEMBER; month++) {
            points.add(new MonthlyPoint(
                    Month.of(month)
                            .getDisplayName(TextStyle.SHORT, new Locale("es")),
                    incomeByMonth.getOrDefault(month, BigDecimal.ZERO),
                    expenseByMonth.getOrDefault(month, BigDecimal.ZERO)
            ));
        }

        return new StatisticsResponse(points);
    }

    /**
     * Builds the "Average income" / "Average expenses" widget.
     *
     * Average = year-to-date total divided by months elapsed.
     * Percentage change compares this month's total against last month's
     * total (matches the "% compared to last month" label in the UI).
     *
     * @param userId the authenticated user's ID.
     * @return the averages response.
     */
    public AveragesResponse getAverages(final UUID userId) {
        userService.validateUserActive(userId);
        List<UUID> walletIds = repositoryWalletUser
                .findWalletIdsByUserId(userId);

        if (walletIds.isEmpty()) {
            return new AveragesResponse(
                    BigDecimal.ZERO, 0.0, BigDecimal.ZERO, 0.0
            );
        }

        LocalDate today = LocalDate.now();
        LocalDate startOfThisMonth = today.withDayOfMonth(1);
        LocalDate endOfLastMonth = startOfThisMonth.minusDays(1);
        LocalDate startOfLastMonth = endOfLastMonth.withDayOfMonth(1);
        LocalDate startOfYear = LocalDate.of(today.getYear(), JANUARY, 1);

        BigDecimal incomeThisMonth = repositoryIncome
                .sumByWalletsAndDateRange(walletIds, startOfThisMonth, today);
        BigDecimal incomeLastMonth = repositoryIncome.sumByWalletsAndDateRange(
                walletIds, startOfLastMonth, endOfLastMonth);

        BigDecimal expenseThisMonth = repositoryExpense
                .sumByWalletsAndDateRange(walletIds, startOfThisMonth, today);
        BigDecimal expenseLastMonth = repositoryExpense
                .sumByWalletsAndDateRange(
                        walletIds, startOfLastMonth, endOfLastMonth
                );

        BigDecimal totalIncomeYtd = repositoryIncome
                .sumByWalletsAndDateRange(walletIds, startOfYear, today);
        BigDecimal totalExpenseYtd = repositoryExpense
                .sumByWalletsAndDateRange(walletIds, startOfYear, today);

        int monthsElapsed = today.getMonthValue();

        BigDecimal avgIncome = totalIncomeYtd.divide(
                BigDecimal.valueOf(monthsElapsed), 2, RoundingMode.HALF_EVEN);
        BigDecimal avgExpense = totalExpenseYtd.divide(
                BigDecimal.valueOf(monthsElapsed), 2, RoundingMode.HALF_EVEN);

        return new AveragesResponse(
                avgIncome,
                calculatePercentageChange(incomeLastMonth, incomeThisMonth),
                avgExpense,
                calculatePercentageChange(expenseLastMonth, expenseThisMonth)
        );
    }

    /**
     * Builds the "Recent activity" widget: the 5 most recent transactions
     * (expenses and incomes combined) across every wallet the user
     * belongs to.
     *
     * @param userId the authenticated user's ID.
     * @return up to 5 recent activity items, most recent first.
     */
    public List<RecentActivityItem> getRecentActivity(final UUID userId) {
        userService.validateUserActive(userId);
        List<UUID> walletIds = repositoryWalletUser
                .findWalletIdsByUserId(userId);

        if (walletIds.isEmpty()) {
            return List.of();
        }

        Pageable limit = PageRequest.of(0, RECENT_ACTIVITY_LIMIT);

        List<Expense> recentExpenses = repositoryExpense
                .findByWalletIdInOrderByCreatedAtDesc(walletIds, limit);
        List<Income> recentIncomes = repositoryIncome
                .findByWalletIdInOrderByCreatedAtDesc(walletIds, limit);

        return Stream.concat(
                        recentExpenses.stream().map(this::toActivityItem),
                        recentIncomes.stream().map(this::toActivityItem)
                )
                .sorted(Comparator.comparing(
                        RecentActivityItem::createdAt).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    private RecentActivityItem toActivityItem(final Expense expense) {
        return new RecentActivityItem(
                "EXPENSE",
                expense.getTitle(),
                expense.getCreatedAt(),
                expense.getAmount().negate(),
                 expense.getWallet().getName(),
                 expense.getCategory() != null
                         ? expense.getCategory().getIcon()
                         : null,
                 expense.getCategory() != null
                         ? expense.getCategory().getColor()
                         : null
        );
    }

    private RecentActivityItem toActivityItem(final Income income) {
        return new RecentActivityItem(
                "INCOME",
                income.getTitle(),
                income.getCreatedAt(),
                income.getAmount(),
                 income.getWallet().getName(),
                 income.getCategory() != null
                         ? income.getCategory().getIcon()
                         : null,
                 income.getCategory() != null
                         ? income.getCategory().getColor()
                         : null
        );
    }

    private Map<Integer, BigDecimal> toMonthMap(
            final List<MonthlyTotalProjection> projections
    ) {
        return projections.stream().collect(Collectors.toMap(
                row -> row.getMonth().intValue(),
                MonthlyTotalProjection::getTotal
        ));
    }

    private Double calculatePercentageChange(
            final BigDecimal previous, final BigDecimal current
    ) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0
                    ? NEW_ACTIVITY_PERCENTAGE : 0.0;
        }
        return current.subtract(previous)
                .divide(
                        previous.abs(),
                        PERCENTAGE_SCALE,
                        RoundingMode.HALF_EVEN
                )
                .multiply(PERCENTAGE_MULTIPLIER)
                .setScale(PERCENTAGE_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    /**
     * Builds the "Spending this month" widget: total spent, spending as a
     * percentage of total activity (income + expenses), and the top 3
     * categories by share of total spending.
     *
     * @param userId the authenticated user's ID.
     * @return the spending summary.
     */
    public SpendingSummaryResponse getSpendingSummary(final UUID userId) {
        userService.validateUserActive(userId);
        List<UUID> walletIds = repositoryWalletUser
                .findWalletIdsByUserId(userId);

        if (walletIds.isEmpty()) {
            return new SpendingSummaryResponse(
                    BigDecimal.ZERO, 0.0, List.of()
            );
        }

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);

        BigDecimal totalExpense = repositoryExpense
                .sumByWalletsAndDateRange(walletIds, startOfMonth, today);
        BigDecimal totalIncome = repositoryIncome
                .sumByWalletsAndDateRange(walletIds, startOfMonth, today);

        BigDecimal totalActivity = totalExpense.add(totalIncome);
        Double activityPercentage = totalActivity
                .compareTo(BigDecimal.ZERO) == 0
                ? 0.0
                : totalExpense.divide(
                        totalActivity, PERCENTAGE_SCALE, RoundingMode.HALF_EVEN)
                .multiply(PERCENTAGE_MULTIPLIER)
                .setScale(PERCENTAGE_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
                .doubleValue();

        List<CategorySpendingProjection> topRaw = repositoryExpense
                .sumGroupedByCategory(
                        walletIds, startOfMonth, today,
                        PageRequest.of(0, TOP_CATEGORIES_LIMIT)
                );

        List<CategoryPercentage> topCategories = topRaw.stream()
                .map(row -> new CategoryPercentage(
                        row.getCategoryId(),
                        row.getCategoryName(),
                        totalExpense.compareTo(BigDecimal.ZERO) == 0
                                ? 0.0
                                : row.getTotal().divide(
                                        totalExpense, PERCENTAGE_SCALE,
                                        RoundingMode.HALF_EVEN)
                                .multiply(PERCENTAGE_MULTIPLIER)
                                .setScale(PERCENTAGE_DISPLAY_SCALE,
                                        RoundingMode.HALF_EVEN)
                                .doubleValue()
                ))
                .toList();

        return new SpendingSummaryResponse(
                totalExpense, activityPercentage, topCategories
        );
    }

    /**
     * Builds the "Budgets health" widget: the 3 most recently updated
     * budgets belonging to the user, with a display name derived from
     * their wallet/category scope.
     *
     * @param userId the authenticated user's ID.
     * @return up to 3 budget health items.
     */
    public List<BudgetHealthItem> getBudgetsHealth(final UUID userId) {
        userService.validateUserActive(userId);

        return repositoryBudget
                .findTop3ByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(budget -> new BudgetHealthItem(
                        budget.getId(),
                        resolveDisplayName(budget),
                        budget.getUpdatedAt(),
                        budget.getLastAlertStatus(),
                        budget.getCurrency()
                ))
                .toList();
    }

    /**
     * Builds the "Activity breakdown" widget: percentage share of incomes,
     * expenses and transfers among all transactions this month, plus the
     * overall change in transaction volume vs last month.
     *
     * @param userId the authenticated user's ID.
     * @return the activity breakdown.
     */
    public ActivityBreakdownResponse getActivityBreakdown(final UUID userId) {
        userService.validateUserActive(userId);
        List<UUID> walletIds = repositoryWalletUser
                .findWalletIdsByUserId(userId);

        if (walletIds.isEmpty()) {
            return new ActivityBreakdownResponse(0, 0.0, 0.0, 0.0, 0.0);
        }

        LocalDate today = LocalDate.now();
        LocalDate startOfThisMonth = today.withDayOfMonth(1);
        LocalDate endOfLastMonth = startOfThisMonth.minusDays(1);
        LocalDate startOfLastMonth = endOfLastMonth.withDayOfMonth(1);

        Instant startOfThisMonthInstant =
                startOfThisMonth.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrowInstant =
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfLastMonthInstant =
                startOfLastMonth.atStartOfDay(ZoneOffset.UTC).toInstant();

        long incomeCount = repositoryIncome.countByWalletsAndDateRange(
                walletIds, startOfThisMonth, today);
        long expenseCount = repositoryExpense.countByWalletsAndDateRange(
                walletIds, startOfThisMonth, today);
        long transferCount = repositoryTransfer.countByWalletsAndDateRange(
                walletIds, startOfThisMonthInstant, startOfTomorrowInstant);

        long totalThisMonth = incomeCount + expenseCount + transferCount;

        long incomeLast = repositoryIncome.countByWalletsAndDateRange(
                walletIds, startOfLastMonth, endOfLastMonth);
        long expenseLast = repositoryExpense.countByWalletsAndDateRange(
                walletIds, startOfLastMonth, endOfLastMonth);
        long transferLast = repositoryTransfer.countByWalletsAndDateRange(
                walletIds, startOfLastMonthInstant, startOfThisMonthInstant);

        long totalLastMonth = incomeLast + expenseLast + transferLast;

        return new ActivityBreakdownResponse(
                totalThisMonth,
                percentageOfTotal(incomeCount, totalThisMonth),
                percentageOfTotal(expenseCount, totalThisMonth),
                percentageOfTotal(transferCount, totalThisMonth),
                calculatePercentageChange(
                        BigDecimal.valueOf(totalLastMonth),
                        BigDecimal.valueOf(totalThisMonth)
                )
        );
    }

    private Double percentageOfTotal(final long part, final long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part)
                .divide(
                        BigDecimal.valueOf(total),
                        PERCENTAGE_SCALE,
                        RoundingMode.HALF_EVEN
                )
                .multiply(PERCENTAGE_MULTIPLIER)
                .setScale(PERCENTAGE_DISPLAY_SCALE, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private String resolveDisplayName(final Budget budget) {
        String walletName = budget.getWallet() != null
                ? budget.getWallet().getName() : null;
        String categoryName = budget.getCategory() != null
                ? budget.getCategory().getName() : null;

        if (walletName != null && categoryName != null) {
            return walletName + " · " + categoryName;
        }
        if (walletName != null) {
            return walletName;
        }
        if (categoryName != null) {
            return categoryName;
        }
        return "Budget";
    }
}
