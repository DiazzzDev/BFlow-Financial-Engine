package Diaz.Dev.BFlow.auth;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserDataExportService;
import bflow.category.entity.Category;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.income.entity.Income;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDataExportServiceTest {
    private RepositoryUser users;
    private RepositoryWalletUser walletUsers;
    private RepositoryExpense expenses;
    private RepositoryIncome incomes;
    private UserDataExportService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        users = mock(RepositoryUser.class);
        walletUsers = mock(RepositoryWalletUser.class);
        expenses = mock(RepositoryExpense.class);
        incomes = mock(RepositoryIncome.class);
        service = new UserDataExportService(users, walletUsers, expenses, incomes);
        userId = UUID.randomUUID();
    }

    @Test
    void exportsProfileWalletsAndOnlyExplicitTransactionFields() {
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setName("User");
        user.setCognitoSub("must-not-be-exported");
        when(users.findById(userId)).thenReturn(Optional.of(user));

        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setName("Personal");
        WalletUser membership = new WalletUser();
        membership.setWallet(wallet);
        when(walletUsers.findByUserId(userId)).thenReturn(List.of(membership));

        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Other");
        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setTitle("Gmail transaction");
        expense.setDescription("Imported");
        expense.setAmount(new BigDecimal("12.34"));
        expense.setDate(LocalDate.of(2026, 11, 9));
        expense.setWallet(wallet);
        expense.setCategory(category);
        expense.setSource("gmail");
        expense.setCreatedAt(Instant.parse("2026-11-09T10:00:00Z"));
        expense.setRecurring(false);
        when(expenses.findByContributorIdOrderByDateAscCreatedAtAsc(userId))
                .thenReturn(List.of(expense));
        when(incomes.findByContributorIdOrderByDateAscCreatedAtAsc(userId))
                .thenReturn(List.of());

        var export = service.export(userId);

        assertThat(export).containsEntry("format", "bflow-user-data-export-v1");
        var profile = (java.util.Map<?, ?>) export.get("profile");
        assertThat(profile.get("email")).isEqualTo("user@example.com");
        assertThat(profile.keySet().contains("cognitoSub")).isFalse();
        assertThat((List<?>) export.get("wallets")).hasSize(1);
        var expenseData = (java.util.Map<?, ?>) ((List<?>) export.get("expenses")).getFirst();
        assertThat(expenseData.get("source")).isEqualTo("gmail");
        assertThat(expenseData.get("categoryName")).isEqualTo("Other");
        assertThat(expenseData.get("amount")).isEqualTo(new BigDecimal("12.34"));
        assertThat(expenseData.keySet().contains("contributor")).isFalse();
        assertThat(expenseData.keySet().contains("receiptFile")).isFalse();
        assertThat((List<?>) export.get("incomes")).isEmpty();
        verify(expenses).findByContributorIdOrderByDateAscCreatedAtAsc(userId);
        verify(incomes).findByContributorIdOrderByDateAscCreatedAtAsc(userId);
    }

    @Test
    void failsWhenRequestedAccountDoesNotExist() {
        when(users.findById(userId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.export(userId))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
