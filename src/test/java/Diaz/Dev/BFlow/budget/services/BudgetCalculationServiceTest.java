package Diaz.Dev.BFlow.budget.services;

import bflow.auth.entities.User;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.budget.enums.PeriodType;
import bflow.budget.services.BudgetCalculationService;
import bflow.budget.services.BudgetLifecycleService;
import bflow.category.entity.Category;
import bflow.common.i18n.MessageService;
import bflow.expenses.RepositoryExpense;
import bflow.wallet.entities.Wallet;
import bflow.wallet.enums.Currency;
import bflow.wallet.repository.RepositoryWalletUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BudgetCalculationService}, focused on the
 * multi-currency / multi-wallet edge cases in CATEGORY_GLOBAL budgets
 * described in the bug report: a CATEGORY_GLOBAL budget with limit 300
 * must never silently sum expenses from wallets in different
 * currencies (e.g. 150 MXN + 150 USD => "spent: 300") into one figure.
 */
@ExtendWith(MockitoExtension.class)
class BudgetCalculationServiceTest {

    @Mock
    private RepositoryExpense repositoryExpense;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private MessageService messageService;

    private BudgetCalculationService service;

    private UUID userId;
    private UUID categoryId;
    private UUID walletId;

    @BeforeEach
    void setUp() {
        // BudgetLifecycleService has no dependencies of its own, so a
        // real instance (not a mock) keeps calculateEndDate's actual
        // DAILY/WEEKLY/MONTHLY math under test instead of stubbing it.
        BudgetLifecycleService lifecycleService =
                new BudgetLifecycleService(messageService);

        service = new BudgetCalculationService(
                repositoryExpense,
                lifecycleService,
                repositoryWalletUser,
                messageService
        );

        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        walletId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------
    // CATEGORY_GLOBAL: the core multi-currency bug
    // ---------------------------------------------------------------

    @Test
    void categoryGlobalOnlySumsWalletsMatchingBudgetCurrency() {
        Budget budget = categoryGlobalBudget(Currency.USD);

        List<UUID> usdWalletIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        when(repositoryWalletUser.findWalletIdsByUserIdAndCurrency(
                userId, Currency.USD
        )).thenReturn(usdWalletIds);

        when(repositoryExpense.sumByWalletsAndCategoryAndDateRange(
                eq(usdWalletIds), eq(categoryId), any(), any()
        )).thenReturn(new BigDecimal("150.00"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(new BigDecimal("150.00"), response.getSpent());

        // Never asked the repository for ALL of the user's wallets
        // without a currency filter — that's exactly the query that
        // caused the original bug.
        verify(repositoryWalletUser, never())
                .findWalletIdsByUserId(any());
    }

    @Test
    void categoryGlobalWithWalletsInAnotherCurrencyExcludesThem() {
        // User has wallets in both USD and MXN. The budget is USD-only.
        // The MXN wallets must never be part of the aggregation.
        Budget budget = categoryGlobalBudget(Currency.USD);

        when(repositoryWalletUser.findWalletIdsByUserIdAndCurrency(
                userId, Currency.USD
        )).thenReturn(List.of(walletId));

        when(repositoryExpense.sumByWalletsAndCategoryAndDateRange(
                eq(List.of(walletId)), eq(categoryId), any(), any()
        )).thenReturn(new BigDecimal("80.00"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(new BigDecimal("80.00"), response.getSpent());
        verify(repositoryWalletUser, never())
                .findWalletIdsByUserIdAndCurrency(userId, Currency.MXN);
    }

    @Test
    void categoryGlobalWithNoWalletsInBudgetCurrencyYieldsZeroSpent() {
        // User's only wallets are MXN, but this budget is USD. There
        // is nothing to sum — spent must be 0, not an error and not a
        // fallback to summing everything.
        Budget budget = categoryGlobalBudget(Currency.USD);

        when(repositoryWalletUser.findWalletIdsByUserIdAndCurrency(
                userId, Currency.USD
        )).thenReturn(List.of());

        BudgetResponse response = service.calculate(budget);

        assertEquals(BigDecimal.ZERO.setScale(0), response.getSpent().setScale(0));
        verify(repositoryExpense, never())
                .sumByWalletsAndCategoryAndDateRange(any(), any(), any(), any());
    }

    @Test
    void categoryGlobalWithNullCurrencyThrowsInsteadOfMixingCurrencies() {
        // Defense in depth: even though the DB column is NOT NULL for
        // new rows, legacy/inconsistent data (or a bypass of the
        // repository layer) must never fall through to summing
        // everything unfiltered.
        Budget budget = categoryGlobalBudget(null);

        when(messageService.get(eq("budget.internal.noCurrencySet"), any()))
                .thenReturn("Budget has no currency set");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.calculate(budget)
        );

        assertEquals(true, ex.getMessage().contains("currency"));
        verify(repositoryWalletUser, never())
                .findWalletIdsByUserIdAndCurrency(any(), any());
    }

    @Test
    void categoryGlobalWithoutCategorySetThrows() {
        Budget budget = categoryGlobalBudget(Currency.USD);
        budget.setCategory(null);

        assertThrows(IllegalStateException.class, () -> service.calculate(budget));
    }

    // ---------------------------------------------------------------
    // WALLET / WALLET_CATEGORY: single-wallet scopes are unaffected
    // by the currency-filtering logic (a wallet only ever has one
    // currency), but are covered here to lock in that calculateSpent
    // still routes to the right repository method per scope.
    // ---------------------------------------------------------------

    @Test
    void walletScopeSumsAllExpensesForWalletRegardlessOfCategory() {
        Budget budget = walletBudget(Currency.MXN);

        when(repositoryExpense.sumExpensesByWalletAndDateRange(
                eq(walletId), any(), any()
        )).thenReturn(new BigDecimal("500.00"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(new BigDecimal("500.00"), response.getSpent());
    }

    @Test
    void walletCategoryScopeSumsOnlyMatchingCategory() {
        Budget budget = walletCategoryBudget(Currency.EUR);

        when(repositoryExpense.sumByWalletAndCategoryAndDateRange(
                eq(walletId), eq(categoryId), any(), any()
        )).thenReturn(new BigDecimal("42.50"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(new BigDecimal("42.50"), response.getSpent());
    }

    // ---------------------------------------------------------------
    // General data-integrity edge cases (not currency-specific, but
    // needed to close the module out end-to-end).
    // ---------------------------------------------------------------

    @Test
    void nullSumFromRepositoryIsTreatedAsZeroNotNull() {
        // COALESCE in the SQL should already prevent this, but the
        // service must not NPE even if a repository implementation
        // ever returns a raw null (e.g. a future native query).
        Budget budget = walletBudget(Currency.USD);

        when(repositoryExpense.sumExpensesByWalletAndDateRange(
                eq(walletId), any(), any()
        )).thenReturn(null);

        BudgetResponse response = service.calculate(budget);

        assertEquals(BigDecimal.ZERO.setScale(0), response.getSpent().setScale(0));
    }

    @Test
    void zeroOrNegativeBudgetAmountIsRejected() {
        Budget budget = walletBudget(Currency.USD);
        budget.setAmount(BigDecimal.ZERO);

        when(repositoryExpense.sumExpensesByWalletAndDateRange(
                eq(walletId), any(), any()
        )).thenReturn(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(budget));
    }

    @Test
    void spendingExactlyAtLimitIsExceededNotCritical() {
        // percentage == 100 must classify as EXCEEDED, not fall
        // through to CRITICAL just because 100 >= thresholdCritical
        // too — boundary ordering in the switch matters.
        Budget budget = walletBudget(Currency.USD);
        budget.setAmount(new BigDecimal("300.00"));

        when(repositoryExpense.sumExpensesByWalletAndDateRange(
                eq(walletId), any(), any()
        )).thenReturn(new BigDecimal("300.00"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(BudgetStatus.EXCEEDED, response.getStatus());
        assertEquals(BigDecimal.ZERO, response.getRemaining());
    }

    @Test
    void overspendingClampsRemainingToZeroInsteadOfGoingNegative() {
        Budget budget = walletBudget(Currency.USD);
        budget.setAmount(new BigDecimal("100.00"));

        when(repositoryExpense.sumExpensesByWalletAndDateRange(
                eq(walletId), any(), any()
        )).thenReturn(new BigDecimal("175.00"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(BigDecimal.ZERO, response.getRemaining());
        assertEquals(BudgetStatus.EXCEEDED, response.getStatus());
    }

    @Test
    void spendingRightAtWarningThresholdIsWarningNotOk() {
        Budget budget = walletBudget(Currency.USD);
        budget.setAmount(new BigDecimal("100.00"));
        budget.setThresholdWarning(70);
        budget.setThresholdCritical(90);

        when(repositoryExpense.sumExpensesByWalletAndDateRange(
                eq(walletId), any(), any()
        )).thenReturn(new BigDecimal("70.00"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(BudgetStatus.WARNING, response.getStatus());
    }

    @Test
    void nullScopeThrowsInsteadOfNullPointerException() {
        Budget budget = walletBudget(Currency.USD);
        budget.setScope(null);

        assertThrows(IllegalStateException.class,
                () -> service.calculate(budget));
    }

    @Test
    void percentageRoundsHalfUpAndTruncatesToInt() {
        // 33.335 / 100 => 33.34% rounded half-up, then truncated to
        // 33 as an int (matches current PercentageDecimal.intValue()
        // behavior) — locking this in so a future refactor of the
        // rounding mode doesn't silently change budget statuses.
        Budget budget = walletBudget(Currency.USD);
        budget.setAmount(new BigDecimal("300.00"));

        when(repositoryExpense.sumExpensesByWalletAndDateRange(
                eq(walletId), any(), any()
        )).thenReturn(new BigDecimal("100.005"));

        BudgetResponse response = service.calculate(budget);

        assertEquals(33, response.getPercentage());
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private Budget categoryGlobalBudget(final Currency currency) {
        Budget budget = baseBudget(currency);
        budget.setScope(BudgetScope.CATEGORY_GLOBAL);
        budget.setWallet(null);

        Category category = new Category();
        category.setId(categoryId);
        budget.setCategory(category);

        return budget;
    }

    private Budget walletBudget(final Currency currency) {
        Budget budget = baseBudget(currency);
        budget.setScope(BudgetScope.WALLET);
        budget.setCategory(null);
        budget.setWallet(wallet(currency));
        return budget;
    }

    private Budget walletCategoryBudget(final Currency currency) {
        Budget budget = baseBudget(currency);
        budget.setScope(BudgetScope.WALLET_CATEGORY);
        budget.setWallet(wallet(currency));

        Category category = new Category();
        category.setId(categoryId);
        budget.setCategory(category);

        return budget;
    }

    private Budget baseBudget(final Currency currency) {
        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());

        User user = new User();
        user.setId(userId);
        budget.setUser(user);

        budget.setCurrency(currency);
        budget.setAmount(new BigDecimal("300.00"));
        budget.setPeriod(PeriodType.MONTHLY);
        budget.setStartDate(LocalDate.of(2026, 8, 1));
        budget.setThresholdWarning(70);
        budget.setThresholdCritical(90);
        budget.setLastAlertStatus(BudgetStatus.OK);

        return budget;
    }

    private Wallet wallet(final Currency currency) {
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrency(currency);
        return wallet;
    }
}