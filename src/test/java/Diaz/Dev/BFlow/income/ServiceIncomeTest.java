package Diaz.Dev.BFlow.income;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.income.DTO.IncomeRequest;
import bflow.income.DTO.IncomeResponse;
import bflow.income.RepositoryIncome;
import bflow.income.ServiceIncome;
import bflow.income.entity.Income;
import bflow.income.enums.IncomeType;
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
 * Unit tests for ServiceIncome.
 */
@ExtendWith(MockitoExtension.class)
class ServiceIncomeTest {

    @Mock
    private RepositoryIncome repositoryIncome;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserServiceImpl userService;

    @InjectMocks
    private ServiceIncome serviceIncome;

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
    void testCreateIncome() {
        // Arrange
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setTitle("Test Income Title");
        request.setAmount(BigDecimal.valueOf(500));
        request.setDescription("Test income");

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setType(IncomeType.SALARY);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setAmount(BigDecimal.valueOf(500));
        income.setDescription("Test income");
        income.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        doNothing().when(serviceWallet).addBalance(any(), any());
        when(repositoryIncome.save(any(Income.class))).thenReturn(income);

        // Act
        IncomeResponse result = serviceIncome.newIncome(request, userId);

        // Assert
        assertNotNull(result);
        verify(userService).validateUserActive(userId);
        verify(repositoryIncome).save(any(Income.class));
    }
}
