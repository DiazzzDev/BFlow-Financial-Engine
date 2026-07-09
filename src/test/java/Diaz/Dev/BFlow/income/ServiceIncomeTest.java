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
import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    private RepositoryCategory repositoryCategory;

    @Mock
    private bflow.category.CategoryValidator categoryValidator;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserServiceImpl userService;

    @InjectMocks
    private ServiceIncome serviceIncome;

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
        category.setName("Salary");
        category.setType(CategoryType.INCOME);
        category.setSystemDefined(true);
        category.setCreatedAt(Instant.now());
    }

    @Test
    void testCreateIncome() {
        // Arrange
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Test Income Title");
        request.setAmount(BigDecimal.valueOf(500));
        request.setDescription("Test income");

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setAmount(BigDecimal.valueOf(500));
        income.setDescription("Test income");
        income.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateIncomeCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
            .thenReturn(Optional.of(wallet));        
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        doNothing().when(serviceWallet).addBalance(any(), any());
        when(repositoryIncome.saveAndFlush(any(Income.class))).thenReturn(income);

        // Act
        IncomeResponse result = serviceIncome.newIncome(request, userId);

        // Assert
        assertNotNull(result);
        verify(userService).validateUserActive(userId);
        verify(repositoryIncome).saveAndFlush(any(Income.class));
    }

    @Test
    void testCreateIncome_lastMomentOfMonth() {
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Year-end bonus");
        request.setAmount(BigDecimal.valueOf(500));
        request.setDate(LocalDate.of(2026, 7, 31)); // último día del mes

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setAmount(BigDecimal.valueOf(500));
        income.setDate(LocalDate.of(2026, 7, 31));
        income.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateIncomeCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        when(repositoryIncome.saveAndFlush(any(Income.class))).thenReturn(income);

        IncomeResponse result = serviceIncome.newIncome(request, userId);

        assertNotNull(result);
        assertEquals(LocalDate.of(2026, 7, 31), income.getDate());
    }

    @Test
    void testCreateIncome_maximumAllowedAmount() {
        // Límite exacto según @Column(precision = 15, scale = 2) en Transaction
        BigDecimal maxAmount = new BigDecimal("9999999999999.99");

        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Large settlement");
        request.setAmount(maxAmount);
        request.setDate(LocalDate.now());

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setAmount(maxAmount);
        income.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateIncomeCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        when(repositoryIncome.saveAndFlush(any(Income.class))).thenReturn(income);

        IncomeResponse result = serviceIncome.newIncome(request, userId);

        assertNotNull(result);
        verify(serviceWallet).addBalance(wallet, maxAmount);
    }

    @Test
    void testIncomeRequest_amountExceedingPrecisionIsRejected() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        IncomeRequest request = new IncomeRequest();
        request.setTitle("Overflow test");
        request.setAmount(new BigDecimal("99999999999999.99")); // 14 enteros, excede el límite de 13
        request.setDate(LocalDate.now());
        request.setWalletId(UUID.randomUUID());
        request.setCategoryId(UUID.randomUUID());

        Set<ConstraintViolation<IncomeRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }
}
