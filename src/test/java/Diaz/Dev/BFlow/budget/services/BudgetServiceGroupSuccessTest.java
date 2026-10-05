package Diaz.Dev.BFlow.budget.services;

import bflow.auth.entities.User;
import bflow.auth.services.UserService;
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
import bflow.expenses.RepositoryExpense;
import bflow.notifications.service.NotificationService;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the group-budget-celebration behavior added to
 * {@link BudgetService#evaluateBudgetsForWallet(UUID)}: a completed,
 * non-exceeded period on a shared wallet should notify every member
 * of that wallet, not just the budget's owner.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceGroupSuccessTest {

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

    private UUID walletId;
    private Wallet wallet;
    private User owner;
    private Budget budget;

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

        walletId = UUID.randomUUID();

        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Household");
        wallet.setCurrency(Currency.USD);

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmail("owner@example.com");
        owner.setName("Owner");

        budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setUser(owner);
        budget.setWallet(wallet);
        budget.setScope(BudgetScope.WALLET);
        budget.setPeriod(PeriodType.MONTHLY);
        budget.setAmount(BigDecimal.valueOf(500));
        budget.setStartDate(LocalDate.now().minusMonths(1));

        // Period has already ended as of "today".
        lenient().when(lifecycleService.calculateEndDate(budget))
                .thenReturn(LocalDate.now().minusDays(1));
        lenient().when(repositoryBudget.findByWalletId(walletId))
                .thenReturn(List.of(budget));
    }

    private WalletUser memberOf(final Wallet w, final WalletRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setName("Member");

        WalletUser wu = new WalletUser();
        wu.setWallet(w);
        wu.setUser(user);
        wu.setRole(role);
        return wu;
    }

    @Test
    void evaluateBudgetsForWallet_multiMemberWallet_notEnded_sendsGroupSuccess() {
        BudgetResponse okResponse = new BudgetResponse();
        okResponse.setStatus(BudgetStatus.OK);
        when(calculationService.calculate(budget)).thenReturn(okResponse);

        WalletUser ownerLink = memberOf(wallet, WalletRole.OWNER);
        ownerLink.setUser(owner);
        WalletUser memberLink = memberOf(wallet, WalletRole.MEMBER);

        when(repositoryWalletUser.findByWalletId(walletId))
                .thenReturn(List.of(ownerLink, memberLink));

        budgetService.evaluateBudgetsForWallet(walletId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> membersCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(notificationService).sendBudgetGroupSuccess(
                membersCaptor.capture(), eq("Household"), eq(okResponse));

        assertEquals(2, membersCaptor.getValue().size());
        assertTrue(membersCaptor.getValue().contains(owner));

        verify(notificationService, never())
                .sendBudgetSuccess(any(), any());
    }

    @Test
    void evaluateBudgetsForWallet_singleMemberWallet_sendsPersonalSuccess() {
        BudgetResponse okResponse = new BudgetResponse();
        okResponse.setStatus(BudgetStatus.OK);
        when(calculationService.calculate(budget)).thenReturn(okResponse);

        WalletUser ownerLink = memberOf(wallet, WalletRole.OWNER);
        ownerLink.setUser(owner);

        when(repositoryWalletUser.findByWalletId(walletId))
                .thenReturn(List.of(ownerLink));

        budgetService.evaluateBudgetsForWallet(walletId);

        verify(notificationService)
                .sendBudgetSuccess(owner.getId(), okResponse);
        verify(notificationService, never())
                .sendBudgetGroupSuccess(any(), any(), any());
    }

    @Test
    void evaluateBudgetsForWallet_budgetExceeded_sendsNoSuccessNotification() {
        BudgetResponse exceededResponse = new BudgetResponse();
        exceededResponse.setStatus(BudgetStatus.EXCEEDED);
        when(calculationService.calculate(budget))
                .thenReturn(exceededResponse);

        WalletUser ownerLink = memberOf(wallet, WalletRole.OWNER);
        ownerLink.setUser(owner);
        WalletUser memberLink = memberOf(wallet, WalletRole.MEMBER);

        lenient().when(repositoryWalletUser.findByWalletId(walletId))
                .thenReturn(List.of(ownerLink, memberLink));

        budgetService.evaluateBudgetsForWallet(walletId);

        verify(notificationService, never())
                .sendBudgetGroupSuccess(any(), any(), any());
        verify(notificationService, never())
                .sendBudgetSuccess(any(), any());
    }
}
