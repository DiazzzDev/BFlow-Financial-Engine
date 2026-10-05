package Diaz.Dev.BFlow.budget.services;

import bflow.auth.entities.User;
import bflow.auth.services.UserService;
import bflow.budget.DTO.BudgetPatchRequest;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.budget.enums.PeriodType;
import bflow.budget.repository.RepositoryBudget;
import bflow.budget.services.BudgetAlertService;
import bflow.budget.services.BudgetCalculationService;
import bflow.budget.services.BudgetLifecycleService;
import bflow.budget.services.BudgetOverlapValidationService;
import bflow.budget.services.BudgetService;
import bflow.budget.services.BudgetValidationService;
import bflow.common.exception.BudgetNotFoundException;
import bflow.common.exception.InvalidBudgetScopeException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.expenses.RepositoryExpense;
import bflow.notifications.service.NotificationService;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.repository.RepositoryWalletUser;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BudgetService}, focused on how currency is
 * resolved, validated, and persisted for {@code createBudget} and
 * {@code patchBudget} across the three budget scopes.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private RepositoryBudget repositoryBudget;

    @Mock
    private BudgetCalculationService calculationService;

    @Mock
    private BudgetAlertService alertService;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BudgetValidationService validationService;

    @Mock
    private BudgetLifecycleService lifecycleService;

    @Mock
    private BudgetOverlapValidationService overlapValidationService;

    @Mock
    private PlanLimitService planLimitService;

    @Mock
    private RepositoryExpense repositoryExpense;

    @Mock
    private EntityManager entityManager;

    private BudgetService budgetService;

    private UUID userId;
    private UUID walletId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(
                repositoryBudget,
                calculationService,
                alertService,
                repositoryWalletUser,
                userService,
                notificationService,
                validationService,
                lifecycleService,
                overlapValidationService,
                planLimitService,
                repositoryExpense,
                entityManager,
                java.time.Clock.systemDefaultZone()
        );

        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        // Common stubs shared by every test — not every test reaches
        // these call sites (tests that fail fast on wallet access or
        // currency validation never touch save/saveAndFlush/
        // calculate), so they're lenient rather than strict.
        lenient().when(repositoryBudget.saveAndFlush(any(Budget.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repositoryBudget.save(any(Budget.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(calculationService.calculate(any(Budget.class)))
                .thenReturn(new BudgetResponse());
    }

    // ---------------------------------------------------------------
    // createBudget — currency wiring
    // ---------------------------------------------------------------

    @Test
    void createBudget_walletScope_persistsWalletCurrencyAsSourceOfTruth() {
        BudgetRequest request = walletScopedRequest(Currency.EUR);

        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser(Currency.EUR)));

        budgetService.createBudget(request, userId, walletId);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(repositoryBudget).saveAndFlush(captor.capture());

        assertEquals(Currency.EUR, captor.getValue().getCurrency());
        verify(validationService).validateCurrency(
                BudgetScope.WALLET, Currency.EUR, Currency.EUR
        );
    }

    @Test
    void createBudget_categoryGlobalScope_usesRequestCurrencyWithoutWalletLookup() {
        BudgetRequest request = categoryGlobalRequest(Currency.MXN);

        budgetService.createBudget(request, userId, null);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(repositoryBudget).saveAndFlush(captor.capture());

        assertEquals(Currency.MXN, captor.getValue().getCurrency());
        verify(repositoryWalletUser, never())
                .findByWalletIdAndUserId(any(), any());
        verify(validationService).validateCurrency(
                BudgetScope.CATEGORY_GLOBAL, Currency.MXN, null
        );
    }

    @Test
    void createBudget_currencyMismatchWithWallet_neverPersistsBudget() {
        BudgetRequest request = walletScopedRequest(Currency.USD);

        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser(Currency.MXN)));

        org.mockito.Mockito.doThrow(
                new InvalidBudgetScopeException(
                        "Budget currency (USD) must match the wallet's "
                                + "currency (MXN)"
                )
        ).when(validationService).validateCurrency(
                BudgetScope.WALLET, Currency.USD, Currency.MXN
        );

        assertThrows(InvalidBudgetScopeException.class,
                () -> budgetService.createBudget(request, userId, walletId));

        verify(repositoryBudget, never()).saveAndFlush(any());
    }

    @Test
    void createBudget_walletAccessDenied_neverReachesCurrencyValidation() {
        BudgetRequest request = walletScopedRequest(Currency.USD);

        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        assertThrows(WalletAccessDeniedException.class,
                () -> budgetService.createBudget(request, userId, walletId));

        verify(validationService, never())
                .validateCurrency(any(), any(), any());
        verify(repositoryBudget, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------
    // patchBudget — currency wiring across scope/wallet transitions
    // ---------------------------------------------------------------

    @Test
    void patchBudget_walletUnchanged_stillRevalidatesAccessAndCurrency() {
        // Even when the wallet on the patch matches the budget's
        // current wallet, access + currency are re-resolved every
        // time — this is what protects against a user who lost
        // access to the wallet after the budget was created.
        Budget existing = walletBudget(Currency.USD);

        when(repositoryBudget.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser(Currency.USD)));

        BudgetPatchRequest patch = new BudgetPatchRequest();
        patch.setAmount(new BigDecimal("250.00"));

        budgetService.patchBudget(existing.getId(), userId, patch);

        verify(repositoryWalletUser)
                .findByWalletIdAndUserId(walletId, userId);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(repositoryBudget).save(captor.capture());
        assertEquals(Currency.USD, captor.getValue().getCurrency());
    }

    @Test
    void patchBudget_walletNoLongerAccessible_throws() {
        Budget existing = walletBudget(Currency.USD);

        when(repositoryBudget.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        BudgetPatchRequest patch = new BudgetPatchRequest();
        patch.setAmount(new BigDecimal("250.00"));

        assertThrows(WalletAccessDeniedException.class,
                () -> budgetService.patchBudget(existing.getId(), userId, patch));

        verify(repositoryBudget, never()).save(any());
    }

    @Test
    void patchBudget_scopeChangeToCategoryGlobalWithoutNewCurrency_keepsPreviousCurrency() {
        // A budget switching FROM a wallet scope TO CATEGORY_GLOBAL,
        // without the client sending an explicit new currency, keeps
        // whatever currency it already had rather than ending up
        // null (which the NOT NULL column would reject anyway).
        Budget existing = walletBudget(Currency.EUR);

        when(repositoryBudget.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        BudgetPatchRequest patch = new BudgetPatchRequest();
        patch.setScope(BudgetScope.CATEGORY_GLOBAL);
        patch.setCategoryId(categoryId);

        budgetService.patchBudget(existing.getId(), userId, patch);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(repositoryBudget).save(captor.capture());

        assertEquals(Currency.EUR, captor.getValue().getCurrency());
        verify(repositoryWalletUser, never())
                .findByWalletIdAndUserId(any(), any());
    }

    @Test
    void patchBudget_scopeChangeToCategoryGlobalWithExplicitCurrency_overridesPreviousCurrency() {
        Budget existing = walletBudget(Currency.EUR);

        when(repositoryBudget.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        BudgetPatchRequest patch = new BudgetPatchRequest();
        patch.setScope(BudgetScope.CATEGORY_GLOBAL);
        patch.setCategoryId(categoryId);
        patch.setCurrency(Currency.MXN);

        budgetService.patchBudget(existing.getId(), userId, patch);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(repositoryBudget).save(captor.capture());

        assertEquals(Currency.MXN, captor.getValue().getCurrency());
    }

    @Test
    void patchBudget_scopeChangeFromCategoryGlobalToWallet_switchesToWalletCurrency() {
        Budget existing = categoryGlobalBudget(Currency.USD);

        when(repositoryBudget.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser(Currency.MXN)));

        BudgetPatchRequest patch = new BudgetPatchRequest();
        patch.setScope(BudgetScope.WALLET);
        patch.setWalletId(walletId);

        budgetService.patchBudget(existing.getId(), userId, patch);

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(repositoryBudget).save(captor.capture());

        // Budget currency must follow the NEW wallet (MXN), not the
        // old CATEGORY_GLOBAL currency (USD) it carried over from.
        assertEquals(Currency.MXN, captor.getValue().getCurrency());
    }

    @Test
    void patchBudget_currencyOnlyChange_resetsAlerts() {
        Budget existing = categoryGlobalBudget(Currency.USD);

        when(repositoryBudget.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        BudgetPatchRequest patch = new BudgetPatchRequest();
        patch.setCurrency(Currency.EUR);

        budgetService.patchBudget(existing.getId(), userId, patch);

        verify(lifecycleService).resetAlerts(existing);
    }

    @Test
    void patchBudget_unknownBudgetId_throwsBudgetNotFound() {
        UUID missingId = UUID.randomUUID();

        when(repositoryBudget.findByIdAndUserId(missingId, userId))
                .thenReturn(Optional.empty());

        BudgetPatchRequest patch = new BudgetPatchRequest();
        patch.setAmount(new BigDecimal("50.00"));

        assertThrows(BudgetNotFoundException.class,
                () -> budgetService.patchBudget(missingId, userId, patch));
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private BudgetRequest walletScopedRequest(final Currency currency) {
        BudgetRequest request = new BudgetRequest();
        request.setScope(BudgetScope.WALLET);
        request.setWalletId(walletId);
        request.setAmount(new BigDecimal("300.00"));
        request.setPeriod(PeriodType.MONTHLY);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setCurrency(currency);
        return request;
    }

    private BudgetRequest categoryGlobalRequest(final Currency currency) {
        BudgetRequest request = new BudgetRequest();
        request.setScope(BudgetScope.CATEGORY_GLOBAL);
        request.setCategoryId(categoryId);
        request.setAmount(new BigDecimal("300.00"));
        request.setPeriod(PeriodType.MONTHLY);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        request.setCurrency(currency);
        return request;
    }

    private WalletUser walletUser(final Currency currency) {
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrency(currency);

        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(wallet);

        User user = new User();
        user.setId(userId);
        walletUser.setUser(user);

        return walletUser;
    }

    private Budget walletBudget(final Currency currency) {
        Budget budget = baseBudget(currency);
        budget.setScope(BudgetScope.WALLET);

        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrency(currency);
        budget.setWallet(wallet);

        return budget;
    }

    private Budget categoryGlobalBudget(final Currency currency) {
        Budget budget = baseBudget(currency);
        budget.setScope(BudgetScope.CATEGORY_GLOBAL);
        budget.setWallet(null);
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
}
