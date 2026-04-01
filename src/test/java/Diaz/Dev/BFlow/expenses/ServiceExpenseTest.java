package Diaz.Dev.BFlow.expenses;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.budget.services.BudgetService;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.ServiceExpense;
import bflow.expenses.entity.Expense;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.category.RepositoryCategory;
import bflow.wallet.RepositoryWallet;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.ServiceWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ServiceExpense.
 */
@ExtendWith(MockitoExtension.class)
class ServiceExpenseTest {

    @Mock
    private RepositoryExpense repositoryExpense;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private RepositoryCategory repositoryCategory;

    @Mock
    private bflow.category.CategoryValidator categoryValidator;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserServiceImpl userService;

    @Mock
    private BudgetService serviceBudget;

    @InjectMocks
    private ServiceExpense serviceExpense;

    private UUID userId;
    private UUID walletId;
    private UUID categoryId;
    private User user;
    private Wallet wallet;
    private WalletUser walletUser;
    private Category category;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setStatus(UserStatus.ACTIVE);

        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Test Wallet");
        wallet.setDescription("Test description");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(BigDecimal.valueOf(1000));
        wallet.setInitialValue(BigDecimal.valueOf(1000));
        wallet.setCreatedAt(Instant.now());

        walletUser = new WalletUser();
        walletUser.setUser(user);
        walletUser.setWallet(wallet);
        walletUser.setRole(WalletRole.OWNER);

        category = new Category();
        category.setId(categoryId);
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);
        category.setSystemDefined(true);
        category.setCreatedAt(Instant.now());
    }

    @Test
    void testCreateExpense() {
        // Arrange
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Test Expense Title");
        request.setAmount(BigDecimal.valueOf(100));
        request.setDescription("Test expense");

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(100));
        expense.setDescription("Test expense");
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateExpenseCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        doNothing().when(serviceWallet).subtractBalance(any(), any());
        when(repositoryExpense.saveAndFlush(any(Expense.class))).thenReturn(expense);

        // Act
        ExpenseResponse result = serviceExpense.newExpense(request, userId);

        // Assert
        assertNotNull(result);
        verify(userService).validateUserActive(userId);
        verify(repositoryExpense).saveAndFlush(any(Expense.class));
    }
}
