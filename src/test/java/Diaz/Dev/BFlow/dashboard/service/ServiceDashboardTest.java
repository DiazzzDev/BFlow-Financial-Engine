package Diaz.Dev.BFlow.dashboard.service;

import bflow.auth.services.UserService;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetStatus;
import bflow.budget.repository.RepositoryBudget;
import bflow.budget.services.BudgetCalculationService;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.i18n.MessageService;
import bflow.dashboard.dto.ActivityTypeFilter;
import bflow.dashboard.dto.BalanceSummaryResponse;
import bflow.dashboard.dto.BudgetHealthItem;
import bflow.dashboard.dto.ActivityBreakdownResponse;
import bflow.dashboard.dto.AveragesResponse;
import bflow.dashboard.dto.MonthlyPoint;
import bflow.dashboard.dto.RecentActivityItem;
import bflow.dashboard.dto.SpendingSummaryResponse;
import bflow.dashboard.dto.StatisticsResponse;
import bflow.dashboard.enums.StatisticsPeriod;
import bflow.dashboard.projection.CategorySpendingProjection;
import bflow.dashboard.projection.DailyTotalProjection;
import bflow.dashboard.projection.MonthlyTotalProjection;
import bflow.dashboard.service.ServiceDashboard;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.income.entity.Income;
import bflow.tranfers.RepositoryTransfers;
import bflow.wallet.entities.Wallet;
import bflow.wallet.enums.Currency;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contract tests for dashboard filtering, activity and budget-health widgets. */
@ExtendWith(MockitoExtension.class)
class ServiceDashboardTest {

    @Mock private RepositoryWalletUser repositoryWalletUser;
    @Mock private RepositoryWallet repositoryWallet;
    @Mock private RepositoryExpense repositoryExpense;
    @Mock private RepositoryIncome repositoryIncome;
    @Mock private RepositoryBudget repositoryBudget;
    @Mock private UserService userService;
    @Mock private RepositoryTransfers repositoryTransfers;
    @Mock private BudgetCalculationService budgetCalculationService;
    @Mock private MessageService messageService;

    private ServiceDashboard service;
    private UUID userId;
    private UUID firstWalletId;
    private UUID secondWalletId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),
                ZoneId.of("America/El_Salvador"));
        service = new ServiceDashboard(repositoryWalletUser, repositoryWallet,
                repositoryExpense, repositoryIncome, repositoryBudget, userService,
                repositoryTransfers, budgetCalculationService, messageService, clock);
        userId = UUID.randomUUID();
        firstWalletId = UUID.randomUUID();
        secondWalletId = UUID.randomUUID();
    }

    @Test
    void balanceSummary_rejectsForeignWalletAndScopesBalanceAndMonthTotals() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId));

        assertThrows(WalletAccessDeniedException.class,
                () -> service.getBalanceSummary(userId, secondWalletId));

        when(repositoryWallet.sumBalanceByWalletIds(List.of(firstWalletId)))
                .thenReturn(new BigDecimal("75.00"));
        when(repositoryIncome.sumByWalletsAndDateRange(
                eq(List.of(firstWalletId)), any(), any()))
                .thenReturn(new BigDecimal("20.00"));
        when(repositoryExpense.sumByWalletsAndDateRange(
                eq(List.of(firstWalletId)), any(), any()))
                .thenReturn(new BigDecimal("5.00"));

        BalanceSummaryResponse response = service.getBalanceSummary(userId, firstWalletId);

        assertEquals(new BigDecimal("75.00"), response.total());
        assertEquals(new BigDecimal("20.00"), response.monthIncome());
        assertEquals(new BigDecimal("5.00"), response.monthExpenses());
        assertEquals(null, response.percentageChangeLastMonth());
    }

    @Test
    void statistics_yearFillsEveryMonthAndUsesZeroForMissingWallets() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(List.of());

        StatisticsResponse response = service.getStatistics(
                userId, null, null, null, null, null, null);

        assertEquals(StatisticsPeriod.YEAR, response.period());
        assertEquals(LocalDate.of(2026, 1, 1), response.startDate());
        assertEquals(LocalDate.of(2026, 12, 31), response.endDate());
        assertEquals(12, response.points().size());
        assertEquals(BigDecimal.ZERO, response.points().getFirst().income());
        assertEquals(BigDecimal.ZERO, response.points().getFirst().expense());
    }

    @Test
    void statistics_customRangeMapsDailyTotalsAndRejectsOversizedRange() {
        LocalDate start = LocalDate.of(2026, 10, 4);
        LocalDate end = LocalDate.of(2026, 10, 6);
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId));
        DailyTotalProjection income = org.mockito.Mockito.mock(
                DailyTotalProjection.class);
        when(income.getDate()).thenReturn(start);
        when(income.getTotal()).thenReturn(new BigDecimal("25.00"));
        when(repositoryIncome.sumGroupedByDate(
                List.of(firstWalletId), start, end))
                .thenReturn(List.of(income));
        when(repositoryExpense.sumGroupedByDate(
                List.of(firstWalletId), start, end)).thenReturn(List.of());

        StatisticsResponse response = service.getStatistics(
                userId, StatisticsPeriod.CUSTOM, null, null, null, start, end);

        assertEquals(3, response.points().size());
        assertEquals(new BigDecimal("25.00"), response.points().getFirst().income());
        assertEquals(BigDecimal.ZERO, response.points().getFirst().expense());
        assertEquals("5 oct", response.points().get(1).month());
        assertThrows(bflow.dashboard.exception.InvalidStatisticsFilterException.class,
                () -> service.getStatistics(userId, StatisticsPeriod.CUSTOM,
                        null, null, null, start, start.plusDays(366)));
    }

    @Test
    void averagesCalculatesYearToDateAndNewActivityPercentages() {
        List<UUID> walletIds = List.of(firstWalletId);
        LocalDate today = LocalDate.of(2026, 10, 5);
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(walletIds);
        when(repositoryIncome.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 10, 1), today))
                .thenReturn(new BigDecimal("20.00"));
        when(repositoryIncome.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(BigDecimal.ZERO);
        when(repositoryIncome.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 1, 1), today))
                .thenReturn(new BigDecimal("100.00"));
        when(repositoryExpense.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 10, 1), today))
                .thenReturn(new BigDecimal("30.00"));
        when(repositoryExpense.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(new BigDecimal("60.00"));
        when(repositoryExpense.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 1, 1), today))
                .thenReturn(new BigDecimal("200.00"));

        AveragesResponse response = service.getAverages(userId);

        assertEquals(new BigDecimal("10.00"), response.averageIncome());
        assertEquals(100.0, response.incomePercentageChangeLastMonth());
        assertEquals(new BigDecimal("20.00"), response.averageExpenses());
        assertEquals(-50.0, response.expensesPercentageChangeLastMonth());
    }

    @Test
    void recentActivity_appliesTypeSearchLimitOrderAndEmptyWalletHandling() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId));
        Expense oldExpense = expense("Coffee old", LocalDate.of(2026, 10, 3),
                Instant.parse("2026-10-03T10:00:00Z"));
        Expense latestExpense = expense("Coffee latest", LocalDate.of(2026, 10, 4),
                Instant.parse("2026-10-04T10:00:00Z"));
        when(repositoryExpense.searchRecent(eq(List.of(firstWalletId)),
                eq("%coffee%"), any(Pageable.class)))
                .thenReturn(List.of(oldExpense, latestExpense));

        List<RecentActivityItem> response = service.getRecentActivity(
                userId, ActivityTypeFilter.EXPENSE, " Coffee ", 1);

        assertEquals(1, response.size());
        assertEquals("Coffee latest", response.getFirst().name());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repositoryExpense).searchRecent(eq(List.of(firstWalletId)),
                eq("%coffee%"), pageable.capture());
        assertEquals(1, pageable.getValue().getPageSize());

        UUID emptyUser = UUID.randomUUID();
        when(repositoryWalletUser.findWalletIdsByUserId(emptyUser)).thenReturn(List.of());
        assertEquals(List.of(), service.getRecentActivity(
                emptyUser, ActivityTypeFilter.ALL, null, 5));
    }

    @Test
    void budgetsHealth_exposesNamesAndOrdersByRisk() {
        Budget lowRisk = budget("Cash", "Food", Instant.parse("2026-10-04T00:00:00Z"));
        Budget highRisk = budget("Card", "Transport", Instant.parse("2026-10-03T00:00:00Z"));
        when(repositoryBudget.findByUserId(userId)).thenReturn(List.of(lowRisk, highRisk));
        when(budgetCalculationService.calculate(lowRisk)).thenReturn(response(20));
        when(budgetCalculationService.calculate(highRisk)).thenReturn(response(90));

        List<BudgetHealthItem> health = service.getBudgetsHealth(userId);

        assertEquals(2, health.size());
        assertEquals(90, health.getFirst().percentage());
        assertEquals("Card", health.getFirst().walletName());
        assertEquals("Transport", health.getFirst().categoryName());
    }

    @Test
    void spendingSummaryUsesCategorySharesAndEmptyActivityFallback() {
        List<UUID> walletIds = List.of(firstWalletId);
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate today = LocalDate.of(2026, 10, 5);
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(walletIds);
        when(repositoryExpense.sumByWalletsAndDateRange(walletIds, start, today))
                .thenReturn(new BigDecimal("30.00"));
        when(repositoryIncome.sumByWalletsAndDateRange(walletIds, start, today))
                .thenReturn(new BigDecimal("70.00"));
        CategorySpendingProjection category =
                org.mockito.Mockito.mock(CategorySpendingProjection.class);
        when(category.getCategoryId()).thenReturn(UUID.randomUUID());
        when(category.getCategoryName()).thenReturn("Food");
        when(category.getTotal()).thenReturn(new BigDecimal("20.00"));
        when(repositoryExpense.sumGroupedByCategory(
                eq(walletIds), eq(start), eq(today), any()))
                .thenReturn(List.of(category));

        SpendingSummaryResponse response = service.getSpendingSummary(userId);

        assertEquals(new BigDecimal("30.00"), response.totalSpent());
        assertEquals(30.0, response.totalActivityPercentage());
        assertEquals(66.7, response.topCategories().getFirst().percentage());

        UUID emptyUser = UUID.randomUUID();
        when(repositoryWalletUser.findWalletIdsByUserId(emptyUser)).thenReturn(List.of());
        SpendingSummaryResponse empty = service.getSpendingSummary(emptyUser);
        assertEquals(BigDecimal.ZERO, empty.totalSpent());
        assertEquals(0.0, empty.totalActivityPercentage());
        assertEquals(List.of(), empty.topCategories());
    }

    @Test
    void activityBreakdownCombinesThreeTransactionTypesAndZeroWalletFallback() {
        List<UUID> walletIds = List.of(firstWalletId);
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(walletIds);
        when(repositoryIncome.countByWalletsAndDateRange(eq(walletIds), any(), any()))
                .thenReturn(1L, 0L);
        when(repositoryExpense.countByWalletsAndDateRange(eq(walletIds), any(), any()))
                .thenReturn(2L, 1L);
        when(repositoryTransfers.countByWalletsAndDateRange(
                eq(walletIds), any(), any())).thenReturn(1L, 1L);

        ActivityBreakdownResponse response = service.getActivityBreakdown(userId);

        assertEquals(4L, response.totalTransactions());
        assertEquals(25.0, response.incomePercentage());
        assertEquals(50.0, response.expensePercentage());
        assertEquals(25.0, response.transferPercentage());
        assertEquals(100.0, response.activityChangePercentage());

        UUID emptyUser = UUID.randomUUID();
        when(repositoryWalletUser.findWalletIdsByUserId(emptyUser)).thenReturn(List.of());
        ActivityBreakdownResponse empty = service.getActivityBreakdown(emptyUser);
        assertEquals(0L, empty.totalTransactions());
        assertEquals(0.0, empty.activityChangePercentage());
    }

    @Test
    void balanceSummaryWithoutWalletsReturnsZeroesWithoutAggregateQueries() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(List.of());

        BalanceSummaryResponse result = service.getBalanceSummary(userId, null);

        assertEquals(BigDecimal.ZERO, result.total());
        assertEquals(BigDecimal.ZERO, result.monthIncome());
        assertEquals(BigDecimal.ZERO, result.monthExpenses());
        assertEquals(0.0, result.percentageChangeLastMonth());
        org.mockito.Mockito.verifyNoInteractions(repositoryWallet,
                repositoryIncome, repositoryExpense);
    }

    @Test
    void balanceSummaryAllWalletsCalculatesChangeFromOpeningMonthBalance() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId, secondWalletId));
        when(repositoryWallet.sumBalanceByWalletIds(
                List.of(firstWalletId, secondWalletId)))
                .thenReturn(new BigDecimal("100.00"));
        when(repositoryIncome.sumByWalletsAndDateRange(
                List.of(firstWalletId, secondWalletId),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)))
                .thenReturn(new BigDecimal("30.00"));
        when(repositoryExpense.sumByWalletsAndDateRange(
                List.of(firstWalletId, secondWalletId),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)))
                .thenReturn(new BigDecimal("20.00"));

        BalanceSummaryResponse result = service.getBalanceSummary(userId, null);

        assertEquals(11.1, result.percentageChangeLastMonth());
        assertEquals(new BigDecimal("100.00"), result.total());
    }

    @Test
    void balanceSummaryWalletScopeOmitsAggregatePercentage() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId, secondWalletId));
        when(repositoryWallet.sumBalanceByWalletIds(List.of(secondWalletId)))
                .thenReturn(new BigDecimal("50.00"));
        when(repositoryIncome.sumByWalletsAndDateRange(
                eq(List.of(secondWalletId)), any(), any()))
                .thenReturn(BigDecimal.TEN);
        when(repositoryExpense.sumByWalletsAndDateRange(
                eq(List.of(secondWalletId)), any(), any()))
                .thenReturn(BigDecimal.ONE);

        BalanceSummaryResponse result =
                service.getBalanceSummary(userId, secondWalletId);

        assertEquals(null, result.percentageChangeLastMonth());
        verify(repositoryWallet).sumBalanceByWalletIds(List.of(secondWalletId));
    }

    @Test
    void statisticsMonthBuildsCompleteLeapMonthAndMapsDailyResults() {
        List<UUID> walletIds = List.of(firstWalletId);
        LocalDate start = LocalDate.of(2024, 2, 1);
        LocalDate end = LocalDate.of(2024, 2, 29);
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(walletIds);
        DailyTotalProjection income = daily(start.plusDays(28), "42.00");
        when(repositoryIncome.sumGroupedByDate(walletIds, start, end))
                .thenReturn(List.of(income));
        when(repositoryExpense.sumGroupedByDate(walletIds, start, end))
                .thenReturn(List.of());

        StatisticsResponse result = service.getStatistics(
                userId, StatisticsPeriod.MONTH, 2024, 2, null, null, null);

        assertEquals(start, result.startDate());
        assertEquals(end, result.endDate());
        assertEquals(29, result.points().size());
        assertEquals("29", result.points().getLast().month());
        assertEquals(new BigDecimal("42.00"), result.points().getLast().income());
    }

    @Test
    void statisticsWeekUsesIsoMondayThroughSundayAcrossYearBoundary() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId));
        LocalDate monday = LocalDate.of(2026, 12, 28);
        LocalDate sunday = LocalDate.of(2027, 1, 3);
        when(repositoryIncome.sumGroupedByDate(
                List.of(firstWalletId), monday, sunday)).thenReturn(List.of());
        when(repositoryExpense.sumGroupedByDate(
                List.of(firstWalletId), monday, sunday)).thenReturn(List.of());

        StatisticsResponse result = service.getStatistics(
                userId, StatisticsPeriod.WEEK, 2026, null, 53, null, null);

        assertEquals(monday, result.startDate());
        assertEquals(sunday, result.endDate());
        assertEquals(7, result.points().size());
        assertEquals(BigDecimal.ZERO, result.points().getFirst().income());
    }

    @Test
    void statisticsCustomRequiresBothRangeBounds() {
        assertThrows(bflow.dashboard.exception.InvalidStatisticsFilterException.class,
                () -> service.getStatistics(userId, StatisticsPeriod.CUSTOM,
                        null, null, null, LocalDate.of(2026, 1, 1), null));
        assertThrows(bflow.dashboard.exception.InvalidStatisticsFilterException.class,
                () -> service.getStatistics(userId, StatisticsPeriod.CUSTOM,
                        null, null, null, null, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void statisticsRejectsDateBoundsUnlessPeriodIsCustom() {
        assertThrows(bflow.dashboard.exception.InvalidStatisticsFilterException.class,
                () -> service.getStatistics(userId, StatisticsPeriod.MONTH,
                        2026, 10, null, LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 5)));
    }

    @Test
    void statisticsRejectsCustomPeriodWithoutExplicitRange() {
        assertThrows(bflow.dashboard.exception.InvalidStatisticsFilterException.class,
                () -> service.getStatistics(userId, StatisticsPeriod.CUSTOM,
                        null, null, null, null, null));
    }

    @Test
    void statisticsRejectsIsoWeekBeyondWeeksInSelectedYear() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(List.of());

        assertThrows(bflow.dashboard.exception.InvalidStatisticsFilterException.class,
                () -> service.getStatistics(userId, StatisticsPeriod.WEEK,
                        2025, null, 53, null, null));
    }

    @Test
    void statisticsYearAggregatesMonthTotalsAndFillsMissingMonths() {
        List<UUID> walletIds = List.of(firstWalletId);
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(walletIds);
        MonthlyTotalProjection income = monthly(10, "80.00");
        MonthlyTotalProjection expense = monthly(10, "25.00");
        when(repositoryIncome.sumGroupedByMonth(
                walletIds, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(List.of(income));
        when(repositoryExpense.sumGroupedByMonth(
                walletIds, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(List.of(expense));

        StatisticsResponse result = service.getStatistics(
                userId, StatisticsPeriod.YEAR, 2025, null, null, null, null);

        assertEquals(12, result.points().size());
        assertEquals(BigDecimal.ZERO, result.points().getFirst().income());
        assertEquals(new BigDecimal("80.00"), result.points().get(9).income());
        assertEquals(new BigDecimal("25.00"), result.points().get(9).expense());
    }

    @Test
    void averagesWithoutWalletMembershipReturnZeroFallback() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(List.of());

        AveragesResponse result = service.getAverages(userId);

        assertEquals(BigDecimal.ZERO, result.averageIncome());
        assertEquals(0.0, result.incomePercentageChangeLastMonth());
        assertEquals(BigDecimal.ZERO, result.averageExpenses());
        assertEquals(0.0, result.expensesPercentageChangeLastMonth());
        org.mockito.Mockito.verifyNoInteractions(repositoryIncome, repositoryExpense);
    }

    @Test
    void averagesTreatNewActivityFromZeroAsOneHundredPercent() {
        List<UUID> walletIds = List.of(firstWalletId);
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(walletIds);
        when(repositoryIncome.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)))
                .thenReturn(BigDecimal.TEN);
        when(repositoryIncome.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(BigDecimal.ZERO);
        when(repositoryIncome.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 5)))
                .thenReturn(new BigDecimal("100.00"));
        when(repositoryExpense.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)))
                .thenReturn(BigDecimal.ZERO);
        when(repositoryExpense.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(BigDecimal.ZERO);
        when(repositoryExpense.sumByWalletsAndDateRange(
                walletIds, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 5)))
                .thenReturn(BigDecimal.ZERO);

        AveragesResponse result = service.getAverages(userId);

        assertEquals(100.0, result.incomePercentageChangeLastMonth());
        assertEquals(0.0, result.expensesPercentageChangeLastMonth());
        assertEquals(new BigDecimal("10.00"), result.averageIncome());
    }

    @Test
    void recentActivityIncomeFilterReturnsPositiveIncomeAndClampsLimit() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId));
        Income salary = income("Salary", LocalDate.of(2026, 10, 4),
                Instant.parse("2026-10-04T15:00:00Z"));
        when(repositoryIncome.searchRecent(
                eq(List.of(firstWalletId)), eq("%salary%"), any(Pageable.class)))
                .thenReturn(List.of(salary));

        List<RecentActivityItem> result = service.getRecentActivity(
                userId, ActivityTypeFilter.INCOME, " salary ", 0);

        assertEquals(1, result.size());
        assertEquals("INCOME", result.getFirst().type());
        assertEquals(new BigDecimal("20.00"), result.getFirst().amount());
        verify(repositoryExpense, org.mockito.Mockito.never())
                .searchRecent(any(), any(), any());
    }

    @Test
    void recentActivityAllCombinesKindsSortsAndLimitsAcrossRepositories() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId));
        Expense expense = expense("Dinner", LocalDate.of(2026, 10, 4),
                Instant.parse("2026-10-04T18:00:00Z"));
        Income income = income("Refund", LocalDate.of(2026, 10, 4),
                Instant.parse("2026-10-04T19:00:00Z"));
        when(repositoryExpense.searchRecent(
                eq(List.of(firstWalletId)), eq("%"), any(Pageable.class)))
                .thenReturn(List.of(expense));
        when(repositoryIncome.searchRecent(
                eq(List.of(firstWalletId)), eq("%"), any(Pageable.class)))
                .thenReturn(List.of(income));

        List<RecentActivityItem> result = service.getRecentActivity(
                userId, ActivityTypeFilter.ALL, null, 1);

        assertEquals(1, result.size());
        assertEquals("INCOME", result.getFirst().type());
    }

    @Test
    void spendingSummaryWithNoAmountsReturnsZeroPercentages() {
        List<UUID> walletIds = List.of(firstWalletId);
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate today = LocalDate.of(2026, 10, 5);
        when(repositoryWalletUser.findWalletIdsByUserId(userId)).thenReturn(walletIds);
        when(repositoryExpense.sumByWalletsAndDateRange(walletIds, start, today))
                .thenReturn(BigDecimal.ZERO);
        when(repositoryIncome.sumByWalletsAndDateRange(walletIds, start, today))
                .thenReturn(BigDecimal.ZERO);
        when(repositoryExpense.sumGroupedByCategory(
                eq(walletIds), eq(start), eq(today), any(Pageable.class)))
                .thenReturn(List.of());

        SpendingSummaryResponse result = service.getSpendingSummary(userId);

        assertEquals(BigDecimal.ZERO, result.totalSpent());
        assertEquals(0.0, result.totalActivityPercentage());
        assertEquals(List.of(), result.topCategories());
    }

    @Test
    void recentActivityTruncatesLongSearchAndReturnsExpenseAsNegativeAmount() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of(firstWalletId));
        String query = "A".repeat(120);
        Expense expense = expense("Market purchase",
                LocalDate.of(2026, 10, 4),
                Instant.parse("2026-10-04T18:00:00Z"));
        when(repositoryExpense.searchRecent(
                eq(List.of(firstWalletId)),
                eq("%" + "a".repeat(100) + "%"),
                any(Pageable.class)))
                .thenReturn(List.of(expense));

        List<RecentActivityItem> result = service.getRecentActivity(
                userId, ActivityTypeFilter.EXPENSE, query, 100);

        assertEquals(1, result.size());
        assertEquals("EXPENSE", result.getFirst().type());
        assertEquals("Market purchase", result.getFirst().name());
        assertEquals(BigDecimal.TEN.negate(), result.getFirst().amount());
        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
        verify(repositoryExpense).searchRecent(
                eq(List.of(firstWalletId)),
                eq("%" + "a".repeat(100) + "%"), pageable.capture());
        assertEquals(50, pageable.getValue().getPageSize());
    }

    @Test
    void customStatisticsAcceptsMaximumRangeAndRejectsReversedDates() {
        when(repositoryWalletUser.findWalletIdsByUserId(userId))
                .thenReturn(List.of());
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = start.plusDays(365);

        StatisticsResponse response = service.getStatistics(
                userId, StatisticsPeriod.CUSTOM, null, null, null, start, end);

        assertEquals(start, response.startDate());
        assertEquals(end, response.endDate());
        assertEquals(366, response.points().size());
        assertThrows(bflow.dashboard.exception.InvalidStatisticsFilterException.class,
                () -> service.getStatistics(userId, StatisticsPeriod.CUSTOM,
                        null, null, null, end, start));
    }

    private DailyTotalProjection daily(final LocalDate date, final String total) {
        DailyTotalProjection projection = org.mockito.Mockito.mock(
                DailyTotalProjection.class);
        when(projection.getDate()).thenReturn(date);
        when(projection.getTotal()).thenReturn(new BigDecimal(total));
        return projection;
    }

    private MonthlyTotalProjection monthly(final int month, final String total) {
        MonthlyTotalProjection projection = org.mockito.Mockito.mock(
                MonthlyTotalProjection.class);
        when(projection.getMonth()).thenReturn(month);
        when(projection.getTotal()).thenReturn(new BigDecimal(total));
        return projection;
    }

    private Income income(final String title, final LocalDate date,
            final Instant createdAt) {
        Wallet wallet = new Wallet();
        wallet.setName("Cash");
        wallet.setCurrency(Currency.USD);
        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setTitle(title);
        income.setDate(date);
        income.setCreatedAt(createdAt);
        income.setAmount(new BigDecimal("20.00"));
        income.setWallet(wallet);
        income.setSource("manual");
        return income;
    }

    private Expense expense(
            final String title, final LocalDate date, final Instant createdAt
    ) {
        Wallet wallet = new Wallet();
        wallet.setName("Cash");
        wallet.setCurrency(Currency.USD);
        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setTitle(title);
        expense.setDate(date);
        expense.setCreatedAt(createdAt);
        expense.setAmount(BigDecimal.TEN);
        expense.setWallet(wallet);
        expense.setSource("manual");
        return expense;
    }

    private Budget budget(
            final String walletName, final String categoryName, final Instant updatedAt
    ) {
        Wallet wallet = new Wallet();
        wallet.setName(walletName);
        bflow.category.entity.Category category = new bflow.category.entity.Category();
        category.setName(categoryName);
        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setWallet(wallet);
        budget.setCategory(category);
        budget.setCurrency(Currency.USD);
        budget.setAmount(BigDecimal.valueOf(100));
        budget.setUpdatedAt(updatedAt);
        return budget;
    }

    private BudgetResponse response(final int percentage) {
        BudgetResponse response = new BudgetResponse();
        response.setPercentage(percentage);
        response.setSpent(BigDecimal.TEN);
        response.setRemaining(BigDecimal.valueOf(90));
        response.setStatus(BudgetStatus.OK);
        return response;
    }
}
