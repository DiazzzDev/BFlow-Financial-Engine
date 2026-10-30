package Diaz.Dev.BFlow.income;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.financial.InvalidTransactionDateException;
import bflow.common.financial.TransactionDatePolicy;
import bflow.income.DTO.IncomeRequest;
import bflow.income.DTO.IncomeResponse;
import bflow.income.RepositoryIncome;
import bflow.income.ServiceIncome;
import bflow.income.entity.Income;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.category.RepositoryCategory;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringLinkService;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;
import bflow.wallet.service.WalletLockService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ValidatorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
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
    private WalletLockService walletLockService;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private RepositoryCategory repositoryCategory;

    @Mock
    private bflow.category.CategoryValidator categoryValidator;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserService userService;

    @Mock
    private RecurringLinkService recurringLinkService;

    @Mock
    private TransactionDatePolicy transactionDatePolicy;

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
        LocalDate transactionDate = LocalDate.of(2026, 10, 5);
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Test Income Title");
        request.setAmount(BigDecimal.valueOf(500));
        request.setDescription("Test income");
        request.setDate(transactionDate);

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setAmount(BigDecimal.valueOf(500));
        income.setDescription("Test income");
        income.setDate(transactionDate);
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
        assertEquals(transactionDate, result.getDate());
        verify(transactionDatePolicy).validateRequired(transactionDate);
        verify(userService).validateUserActive(userId);
        verify(repositoryIncome).saveAndFlush(any(Income.class));
    }

    @Test
    void invalidHistoricalDateDoesNotPersistIncomeOrChangeBalance() {
        IncomeRequest request = new IncomeRequest();
        request.setDate(LocalDate.of(1500, 1, 1));
        doThrow(new InvalidTransactionDateException(
                "date must be within the supported range"))
                .when(transactionDatePolicy).validateRequired(request.getDate());

        assertThrows(InvalidTransactionDateException.class,
                () -> serviceIncome.newIncome(request, userId));

        verify(repositoryIncome, never()).saveAndFlush(any());
        verify(serviceWallet, never()).addBalance(any(), any());
    }

    @Test
    void oneDayBeforeHistoricalLimitCannotCreateIncome() {
        LocalDate invalidDate = LocalDate.of(1976, 10, 4);
        IncomeRequest request = new IncomeRequest();
        request.setDate(invalidDate);
        doThrow(new InvalidTransactionDateException(
                "date must be within the supported range"))
                .when(transactionDatePolicy).validateRequired(invalidDate);

        assertThrows(InvalidTransactionDateException.class,
                () -> serviceIncome.newIncome(request, userId));

        verify(repositoryIncome, never()).saveAndFlush(any());
        verify(serviceWallet, never()).addBalance(any(), any());
    }

    @Test
    void futureDateDoesNotUpdateIncomeOrChangeBalance() {
        IncomeRequest request = new IncomeRequest();
        request.setDate(LocalDate.of(9999, 12, 31));
        doThrow(new InvalidTransactionDateException(
                "date must be within the supported range"))
                .when(transactionDatePolicy).validateRequired(request.getDate());

        assertThrows(InvalidTransactionDateException.class,
                () -> serviceIncome.updateIncome(
                        UUID.randomUUID(), request, userId));

        verify(repositoryIncome, never()).save(any());
        verify(serviceWallet, never()).adjustBalanceForIncomeUpdate(
                any(), any(), any());
        verify(serviceWallet, never()).addBalance(any(), any());
    }

    @Test
    void testCreateIncome_pendingDeletionUserIsRejectedBeforeWalletMutation() {
        user.setStatus(UserStatus.PENDING_DELETION);
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        doThrow(new IllegalStateException("inactive"))
                .when(userService).validateUserActive(userId);

        assertThrows(IllegalStateException.class,
                () -> serviceIncome.newIncome(request, userId));

        verify(repositoryWalletUser, never())
                .findByWalletIdAndUserId(any(), any());
        verify(repositoryWallet, never()).findByIdForUpdate(any());
        verify(serviceWallet, never()).addBalance(any(), any());
        verify(repositoryIncome, never()).saveAndFlush(any());
    }

    @Test
    void testCreateIncome_withoutOptionalCategoryPersistsMinimalRequest() {
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setTitle("Uncategorized income");
        request.setAmount(new BigDecimal("45.67"));
        request.setDate(LocalDate.of(2026, 10, 5));

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryIncome.saveAndFlush(any(Income.class)))
                .thenAnswer(invocation -> {
                    Income saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        IncomeResponse response = serviceIncome.newIncome(request, userId);

        ArgumentCaptor<Income> savedIncome =
                ArgumentCaptor.forClass(Income.class);
        verify(repositoryIncome).saveAndFlush(savedIncome.capture());
        assertNotNull(response);
        assertNull(savedIncome.getValue().getCategory());
        assertNull(savedIncome.getValue().getDescription());
        verify(repositoryCategory, never()).findById(any());
        verify(categoryValidator, never()).validateIncomeCategory(any());
        verify(serviceWallet).addBalance(wallet, new BigDecimal("45.67"));
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
        request.setDate(LocalDate.of(2026, 10, 5));

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
        jakarta.validation.ValidatorFactory defaultFactory =
                Validation.buildDefaultValidatorFactory();
        TransactionDatePolicy fixedPolicy = new TransactionDatePolicy(
                java.time.Clock.fixed(
                        java.time.Instant.parse("2026-10-05T18:00:00Z"),
                        java.time.ZoneId.of("America/El_Salvador")),
                org.mockito.Mockito.mock(bflow.common.i18n.MessageService.class));
        ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .constraintValidatorFactory(new ConstraintValidatorFactory() {
                    @Override
                    public <T extends jakarta.validation.ConstraintValidator<?, ?>>
                    T getInstance(Class<T> key) {
                        if (key == bflow.common.financial.TransactionDateValidator.class) {
                            return key.cast(new bflow.common.financial.TransactionDateValidator(
                                    fixedPolicy));
                        }
                        return defaultFactory.getConstraintValidatorFactory()
                                .getInstance(key);
                    }

                    @Override
                    public void releaseInstance(
                            jakarta.validation.ConstraintValidator<?, ?> instance) {
                        defaultFactory.getConstraintValidatorFactory()
                                .releaseInstance(instance);
                    }
                }).buildValidatorFactory();
        Validator validator = factory.getValidator();

        IncomeRequest request = new IncomeRequest();
        request.setTitle("Overflow test");
        request.setAmount(new BigDecimal("99999999999999.99")); // 14 enteros, excede el límite de 13
        request.setDate(LocalDate.of(2026, 10, 5));
        request.setWalletId(UUID.randomUUID());
        request.setCategoryId(UUID.randomUUID());

        Set<ConstraintViolation<IncomeRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void testCreateIncome_recurring_createsRecurringLinkAndStoresId() {
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("  Freelance retainer  ");
        request.setDescription("  Monthly consulting  ");
        request.setAmount(new BigDecimal("500.25"));
        request.setDate(LocalDate.of(2026, 8, 17));
        request.setRecurring(true);
        request.setRecurrencePattern("MONTHLY");

        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setId(UUID.randomUUID());

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateIncomeCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(recurringLinkService.linkRecurring(
                any(RecurringLinkService.RecurringCreateRequest.class)))
                .thenReturn(recurring);
        when(repositoryIncome.saveAndFlush(any(Income.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IncomeResponse result = serviceIncome.newIncome(request, userId);

        assertNotNull(result);

        ArgumentCaptor<RecurringLinkService.RecurringCreateRequest> captor =
                ArgumentCaptor.forClass(
                        RecurringLinkService.RecurringCreateRequest.class);
        verify(recurringLinkService).linkRecurring(captor.capture());

        RecurringLinkService.RecurringCreateRequest captured = captor.getValue();
        assertEquals(RecurringType.INCOME, captured.type());
        assertEquals("MONTHLY", captured.rawFrequency());
        assertEquals(wallet, captured.wallet());
        assertEquals(category, captured.category());
        assertEquals(user, captured.user());
        assertEquals("Freelance retainer", captured.title());
        assertEquals("  Monthly consulting  ", captured.description());
        assertEquals(new BigDecimal("500.25"), captured.amount());
        assertEquals(LocalDate.of(2026, 8, 17), captured.startDate());
        ArgumentCaptor<Income> saved =
                ArgumentCaptor.forClass(Income.class);
        verify(repositoryIncome).saveAndFlush(saved.capture());
        assertEquals("Freelance retainer", saved.getValue().getTitle());
        assertEquals("  Monthly consulting  ", saved.getValue().getDescription());
        assertEquals(new BigDecimal("500.25"), saved.getValue().getAmount());
        assertEquals(LocalDate.of(2026, 8, 17), saved.getValue().getDate());
        assertEquals(wallet, saved.getValue().getWallet());
        assertEquals(category, saved.getValue().getCategory());
        assertEquals(user, saved.getValue().getContributor());
        assertTrue(saved.getValue().getRecurring());
        assertEquals("MONTHLY", saved.getValue().getRecurrencePattern());
        assertEquals(recurring.getId(),
                saved.getValue().getRecurringTransactionId());
    }

    @Test
    void testCreateIncome_recurringFalse_neverCallsRecurringLinkService() {
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Regular payout");
        request.setAmount(BigDecimal.valueOf(500));

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setAmount(BigDecimal.valueOf(500));
        income.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateIncomeCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(repositoryIncome.saveAndFlush(any(Income.class)))
                .thenReturn(income);

        serviceIncome.newIncome(request, userId);

        verify(recurringLinkService, never()).linkRecurring(
                any(RecurringLinkService.RecurringCreateRequest.class));
    }

    /**
     * Simulates RecurringTransactionExecutor firing a due recurring
     * income (source="recurring", recurring=true). Must NOT create
     * another RecurringTransaction, or every scheduled execution would
     * spawn a new template indefinitely.
     */
    @Test
    void testCreateIncome_autoGeneratedByExecutor_neverCreatesAnotherLink() {
        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Monthly salary");
        request.setAmount(BigDecimal.valueOf(500));
        request.setRecurring(true);
        request.setRecurrencePattern("MONTHLY");
        request.setSource("recurring");
        request.setDate(LocalDate.of(2026, 10, 5));

        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setContributor(user);
        income.setWallet(wallet);
        income.setAmount(BigDecimal.valueOf(500));
        income.setCreatedAt(Instant.now());

        doNothing().when(userService).validateUserActive(userId);
        doNothing().when(categoryValidator).validateIncomeCategory(category);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));
        when(repositoryCategory.findById(categoryId))
                .thenReturn(Optional.of(category));
        when(repositoryIncome.saveAndFlush(any(Income.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        serviceIncome.newIncome(request, userId);

        verify(recurringLinkService, never()).linkRecurring(
                any(RecurringLinkService.RecurringCreateRequest.class));
        ArgumentCaptor<Income> saved =
                ArgumentCaptor.forClass(Income.class);
        verify(repositoryIncome).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().getAutoGenerated());
        assertTrue(saved.getValue().getRecurring());
        assertEquals("recurring", saved.getValue().getSource());
        assertEquals("MONTHLY", saved.getValue().getRecurrencePattern());
        assertEquals(LocalDate.of(2026, 10, 5), saved.getValue().getDate());
    }

    @Test
    void testUpdateIncome_sameWalletAdjustsOnlyTheDifference() {
        UUID incomeId = UUID.randomUUID();
        Income existing = new Income();
        existing.setId(incomeId);
        existing.setWallet(wallet);
        existing.setCategory(category);
        existing.setContributor(user);
        existing.setAmount(new BigDecimal("25.00"));
        existing.setTitle("Old title");
        existing.setDate(LocalDate.of(2026, 10, 1));
        existing.setCreatedAt(Instant.now());

        IncomeRequest request = new IncomeRequest();
        request.setWalletId(walletId);
        request.setCategoryId(categoryId);
        request.setTitle("Updated title");
        request.setAmount(new BigDecimal("30.00"));
        request.setDate(LocalDate.of(2026, 10, 5));
        request.setRecurring(true);
        request.setRecurrencePattern("MONTHLY");
        UUID recurringId = UUID.randomUUID();

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryIncome.findById(incomeId)).thenReturn(Optional.of(existing));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(walletLockService.lockWallets(walletId, walletId))
                .thenReturn(new bflow.wallet.DTO.WalletPair(wallet, wallet));
        when(repositoryCategory.findById(categoryId)).thenReturn(Optional.of(category));
        when(recurringLinkService.syncOnUpdate(any())).thenReturn(recurringId);

        IncomeResponse response =
                serviceIncome.updateIncome(incomeId, request, userId);

        assertNotNull(response);
        assertEquals("Updated title", existing.getTitle());
        assertEquals(new BigDecimal("30.00"), existing.getAmount());
        assertEquals(category, existing.getCategory());
        assertEquals(recurringId, existing.getRecurringTransactionId());
        assertEquals(true, existing.getRecurring());
        verify(serviceWallet).adjustBalanceForIncomeUpdate(
                wallet, new BigDecimal("25.00"), new BigDecimal("30.00"));
        verify(serviceWallet, never()).subtractBalance(any(), any());
    }

    @Test
    void testDeleteIncome_subtractsAmountFromLockedWallet() {
        UUID incomeId = UUID.randomUUID();
        Income existing = new Income();
        existing.setId(incomeId);
        existing.setWallet(wallet);
        existing.setAmount(new BigDecimal("12.34"));

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryIncome.findById(incomeId)).thenReturn(Optional.of(existing));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));
        when(repositoryWallet.findByIdForUpdate(walletId))
                .thenReturn(Optional.of(wallet));

        serviceIncome.deleteIncome(incomeId, userId);

        verify(serviceWallet).subtractBalance(wallet, new BigDecimal("12.34"));
        verify(repositoryIncome).delete(existing);
    }
}
