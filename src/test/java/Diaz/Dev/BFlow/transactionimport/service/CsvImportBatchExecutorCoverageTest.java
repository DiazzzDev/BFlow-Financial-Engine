package Diaz.Dev.BFlow.transactionimport.service;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.budget.services.BudgetService;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.financial.TransactionDatePolicy;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.income.entity.Income;
import bflow.transactionimport.dto.ImportRowResult;
import bflow.transactionimport.enums.ImportTransactionType;
import bflow.transactionimport.model.ParsedRow;
import bflow.transactionimport.service.CsvImportBatchExecutor;
import bflow.wallet.entities.Wallet;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.service.ServiceWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CsvImportBatchExecutorCoverageTest {

    private RepositoryWallet repositoryWallet;
    private RepositoryUser repositoryUser;
    private RepositoryCategory repositoryCategory;
    private RepositoryExpense repositoryExpense;
    private RepositoryIncome repositoryIncome;
    private ServiceWallet serviceWallet;
    private BudgetService serviceBudget;
    private TransactionDatePolicy transactionDatePolicy;
    private CsvImportBatchExecutor executor;
    private Wallet wallet;
    private User contributor;
    private UUID walletId;
    private UUID userId;
    private UUID categoryId;
    private Category category;

    @BeforeEach
    void setUp() {
        repositoryWallet = mock(RepositoryWallet.class);
        repositoryUser = mock(RepositoryUser.class);
        repositoryCategory = mock(RepositoryCategory.class);
        repositoryExpense = mock(RepositoryExpense.class);
        repositoryIncome = mock(RepositoryIncome.class);
        serviceWallet = mock(ServiceWallet.class);
        serviceBudget = mock(BudgetService.class);
        transactionDatePolicy = mock(TransactionDatePolicy.class);
        executor = new CsvImportBatchExecutor(
                repositoryWallet,
                repositoryUser,
                repositoryCategory,
                repositoryExpense,
                repositoryIncome,
                serviceWallet,
                serviceBudget,
                transactionDatePolicy);
        wallet = new Wallet();
        contributor = new User();
        walletId = UUID.randomUUID();
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        category = new Category();
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryUser.findById(userId))
                .thenReturn(Optional.of(contributor));
        when(repositoryCategory.getReferenceById(categoryId))
                .thenReturn(category);
        when(repositoryExpense.saveAll(anyList())).thenAnswer(invocation -> {
            List<Expense> rows = invocation.getArgument(0);
            rows.forEach(row -> row.setId(UUID.randomUUID()));
            return rows;
        });
        when(repositoryIncome.saveAll(anyList())).thenAnswer(invocation -> {
            List<Income> rows = invocation.getArgument(0);
            rows.forEach(row -> row.setId(UUID.randomUUID()));
            return rows;
        });
    }

    @Test
    void persistsMixedRowsAndAggregatesWalletAndBudgetUpdates() {
        ParsedRow expenseOne = row(
                2, ImportTransactionType.EXPENSE.name(), "Groceries",
                "Market", "10.125");
        ParsedRow income = row(3, ImportTransactionType.INCOME.name(),
                "Refund", null, "5.555");
        ParsedRow expenseTwo = row(
                4, ImportTransactionType.EXPENSE.name(), "Coffee",
                "Cafe", "10.135");

        Map<Integer, ImportRowResult> result = executor.executeBatch(
                walletId, userId, List.of(expenseOne, income, expenseTwo));

        assertEquals(3, result.size());
        assertEquals("EXPENSE", result.get(2).getType());
        assertEquals("INCOME", result.get(3).getType());
        assertEquals("EXPENSE", result.get(4).getType());
        assertEquals(true, result.get(2).isSuccess());
        assertEquals(true, result.get(3).isSuccess());
        assertEquals(true, result.get(4).isSuccess());
        verify(serviceWallet).subtractBalance(
                wallet, new BigDecimal("20.26"));
        verify(serviceWallet).addBalance(wallet, new BigDecimal("5.56"));
        verify(repositoryExpense).flush();
        verify(repositoryIncome).flush();
        verify(serviceBudget).evaluateBudgetsForExpenseEvent(
                walletId, categoryId);
    }

    @Test
    void persistsIncomeOnlyWithoutExpenseOrBudgetOperations() {
        Map<Integer, ImportRowResult> result = executor.executeBatch(
                walletId,
                userId,
                List.of(row(1, ImportTransactionType.INCOME.name(),
                        "Salary", "Monthly", "1000.00")));

        assertEquals(1, result.size());
        assertEquals("INCOME", result.get(1).getType());
        verify(serviceWallet).addBalance(wallet, new BigDecimal("1000.00"));
        verify(repositoryExpense, never()).saveAll(anyList());
        verify(serviceBudget, never())
                .evaluateBudgetsForExpenseEvent(walletId, categoryId);
    }

    @Test
    void emptyBatchReturnsNoOutcomesAfterLoadingWalletAndContributor() {
        assertEquals(Map.of(), executor.executeBatch(
                walletId, userId, List.of()));

        verify(repositoryWallet).findByIdForUpdate(walletId);
        verify(repositoryUser).findById(userId);
        verify(serviceWallet, never()).addBalance(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(serviceWallet, never()).subtractBalance(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingWalletFailsBeforeContributorLookup() {
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                executor.executeBatch(walletId, userId, List.of()));

        verify(repositoryUser, never()).findById(userId);
    }

    @Test
    void missingContributorFailsBeforeSavingRows() {
        when(repositoryUser.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                executor.executeBatch(
                        walletId, userId,
                        List.of(row(1, ImportTransactionType.EXPENSE.name(),
                                "Meal", null, "12.00"))));

        verify(repositoryExpense, never()).saveAll(anyList());
        verify(repositoryIncome, never()).saveAll(anyList());
    }

    @Test
    void rejectsInvalidDatesBeforeLoadingWallet() {
        ParsedRow invalidDate = row(
                1, ImportTransactionType.EXPENSE.name(), "Meal", null, "12.00");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("bad date"))
                .when(transactionDatePolicy).validateRequired(invalidDate.date());

        assertThrows(IllegalArgumentException.class, () ->
                executor.executeBatch(
                        walletId, userId, List.of(invalidDate)));

        verify(repositoryWallet, never()).findByIdForUpdate(walletId);
    }

    private ParsedRow row(
            final int rowNumber,
            final String type,
            final String title,
            final String description,
            final String amount
    ) {
        return new ParsedRow(
                rowNumber, type, title, description,
                new BigDecimal(amount), LocalDate.of(2025, 1, 15), categoryId);
    }
}
