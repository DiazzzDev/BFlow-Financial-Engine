package Diaz.Dev.BFlow.auth.services;

import bflow.auth.SystemUsers;
import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.ServiceAccountHardDelete;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.PeriodType;
import bflow.budget.repository.RepositoryBudget;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.subscription.entities.Payment;
import bflow.subscription.entities.Plan;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.PaymentStatus;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositoryPayment;
import bflow.subscription.repository.RepositoryPlan;
import bflow.subscription.repository.RepositorySubscription;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ServiceAccountHardDelete} against the real
 * devcontainer Postgres (see application-test.properties), rolled
 * back automatically by {@code @Transactional}.
 *
 * <p>Requires V33__ghost_user_and_billing_snapshot.sql to have been
 * applied — Spring Boot runs Flyway on context startup, so a normal
 * {@code mvn test} against the devcontainer DB is enough.</p>
 */
@SpringBootTest(classes = bflow.BFlowApplication.class)
@ActiveProfiles("test")
@Transactional
class ServiceAccountHardDeleteTest {

    @Autowired private RepositoryUser repositoryUser;
    @Autowired private RepositoryWallet repositoryWallet;
    @Autowired private RepositoryWalletUser repositoryWalletUser;
    @Autowired private RepositoryExpense repositoryExpense;
    @Autowired private RepositoryIncome repositoryIncome;
    @Autowired private RepositoryBudget repositoryBudget;
    @Autowired private RepositoryPlan repositoryPlan;
    @Autowired private RepositorySubscription repositorySubscription;
    @Autowired private RepositoryPayment repositoryPayment;
    @Autowired private ServiceAccountHardDelete serviceAccountHardDelete;

    private User leaving;
    private User staying;
    private Wallet sharedWallet;
    private Wallet personalWallet;

    @BeforeEach
    void setUp() {
        leaving = persistUser("leaving@example.com", "Leaving User");
        staying = persistUser("staying@example.com", "Staying User");

        sharedWallet = persistWallet("Shared");
        addMember(sharedWallet, leaving, WalletRole.OWNER);
        addMember(sharedWallet, staying, WalletRole.MEMBER);

        personalWallet = persistWallet("Personal");
        addMember(personalWallet, leaving, WalletRole.OWNER);

        persistExpense(sharedWallet, leaving, "Shared expense");
        persistExpense(personalWallet, leaving, "Personal expense");

        persistBudget(sharedWallet, leaving);

        persistSubscriptionAndPayment(leaving);
    }

    @Test
    void hardDelete_reassignsSharedWalletContributionsToGhost() {
        User ghostBefore = repositoryUser
                .findById(SystemUsers.GHOST_USER_ID).orElseThrow();

        serviceAccountHardDelete.hardDelete(leaving);

        Expense sharedExpense = repositoryExpense
                .findByWalletId(sharedWallet.getId(),
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent().get(0);

        assertEquals(ghostBefore.getId(),
                sharedExpense.getContributor().getId());
    }

    @Test
    void hardDelete_deletesPersonalWalletEntirely() {
        UUID personalWalletId = personalWallet.getId();

        serviceAccountHardDelete.hardDelete(leaving);

        assertTrue(repositoryWallet.findById(personalWalletId).isEmpty());
        assertTrue(repositoryExpense
                .findByWalletId(personalWalletId,
                        org.springframework.data.domain.Pageable.unpaged())
                .isEmpty());
    }

    @Test
    void hardDelete_keepsSharedWalletAliveForRemainingMember() {
        UUID sharedWalletId = sharedWallet.getId();

        serviceAccountHardDelete.hardDelete(leaving);

        assertTrue(repositoryWallet.findById(sharedWalletId).isPresent());
        assertTrue(repositoryWalletUser
                .existsByWalletIdAndUserId(sharedWalletId, staying.getId()));
        assertFalse(repositoryWalletUser
                .existsByWalletIdAndUserId(sharedWalletId, leaving.getId()));
    }

    @Test
    void hardDelete_deletesBudgetsRegardlessOfWalletSharing() {
        serviceAccountHardDelete.hardDelete(leaving);

        assertTrue(repositoryBudget.findByUserId(leaving.getId()).isEmpty());
    }

    @Test
    void hardDelete_snapshotsEmailAndReassignsBillingRecords() {
        String originalEmail = leaving.getEmail();

        serviceAccountHardDelete.hardDelete(leaving);

        Subscription sub = repositorySubscription
                .findByUserId(SystemUsers.GHOST_USER_ID).orElseThrow();

        assertEquals(originalEmail, sub.getBillingEmail());
    }

    @Test
    void hardDelete_removesTheUserRow() {
        UUID userId = leaving.getId();

        serviceAccountHardDelete.hardDelete(leaving);

        assertTrue(repositoryUser.findById(userId).isEmpty());
    }

    // ---- fixtures ----

    private User persistUser(final String email, final String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now().minusSeconds(60));
        user.setEmailVerified(true);
        return repositoryUser.save(user);
    }

    private Wallet persistWallet(final String name) {
        Wallet wallet = new Wallet();
        wallet.setName(name);
        wallet.setDescription("test wallet");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setInitialValue(BigDecimal.ZERO);
        return repositoryWallet.save(wallet);
    }

    private void addMember(
            final Wallet wallet, final User user, final WalletRole role
    ) {
        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(wallet);
        walletUser.setUser(user);
        walletUser.setRole(role);
        repositoryWalletUser.save(walletUser);
    }

    private void persistExpense(
            final Wallet wallet, final User contributor, final String title
    ) {
        Expense expense = new Expense();
        expense.setTitle(title);
        expense.setAmount(new BigDecimal("10.00"));
        expense.setDate(LocalDate.now());
        expense.setWallet(wallet);
        expense.setContributor(contributor);
        expense.setSource("manual");
        repositoryExpense.save(expense);
    }

    private void persistBudget(final Wallet wallet, final User user) {
        Budget budget = new Budget();
        budget.setWallet(wallet);
        budget.setUser(user);
        budget.setPeriod(PeriodType.MONTHLY);
        budget.setAmount(new BigDecimal("100.00"));
        budget.setCurrency(Currency.USD);
        budget.setScope(BudgetScope.WALLET);
        budget.setStartDate(LocalDate.now());
        repositoryBudget.save(budget);
    }

    private void persistSubscriptionAndPayment(final User user) {
        Plan freePlan = repositoryPlan.findByCode("FREE").orElseThrow();

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingAmount(BigDecimal.ZERO);
        subscription.setStartsAt(Instant.now());
        subscription = repositorySubscription.save(subscription);

        Payment payment = new Payment();
        payment.setUser(user);
        payment.setSubscription(subscription);
        payment.setAmount(BigDecimal.ZERO);
        payment.setCurrency("USD");
        payment.setProvider("WOMPI");
        payment.setProviderPaymentId("test-provider-id-" + UUID.randomUUID());
        payment.setReference("test-ref-" + UUID.randomUUID());
        payment.setIdempotencyKey(UUID.randomUUID());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        repositoryPayment.save(payment);
    }
}