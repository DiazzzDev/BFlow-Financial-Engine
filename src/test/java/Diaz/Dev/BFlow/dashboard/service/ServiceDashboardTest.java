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
import bflow.dashboard.dto.RecentActivityItem;
import bflow.dashboard.service.ServiceDashboard;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
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
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"),
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
