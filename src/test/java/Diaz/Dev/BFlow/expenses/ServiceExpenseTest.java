package Diaz.Dev.BFlow.expenses;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.ServiceExpense;
import bflow.expenses.entity.Expense;
import bflow.expenses.enums.ExpenseType;
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
    private ServiceWallet serviceWallet;

    @Mock
    private UserServiceImpl userService;

    @InjectMocks
    private ServiceExpense serviceExpense;

    private UUID userId;
    private UUID walletId;
    private User user;
    private Wallet wallet;
    private WalletUser walletUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();

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
    }

    @Test
    void testCreateExpense() {
        // Arrange
        ExpenseRequest request = new ExpenseRequest();
        request.setWalletId(walletId);
        request.setTitle("Test Expense Title");
        request.setAmount(BigDecimal.valueOf(100));
        request.setDescription("Test expense");

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setType(ExpenseType.FOOD);
        expense.setContributor(user);
        expense.setWallet(wallet);
        expense.setAmount(BigDecimal.valueOf(100));
        expense.setDescription("Test expense");
        expense.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        doNothing().when(serviceWallet).subtractBalance(any(), any());
        when(repositoryExpense.save(any(Expense.class))).thenReturn(expense);

        // Act
        ExpenseResponse result = serviceExpense.newExpense(request, userId);

        // Assert
        assertNotNull(result);
        verify(userService).validateUserActive(userId);
        verify(repositoryExpense).save(any(Expense.class));
    }
}
