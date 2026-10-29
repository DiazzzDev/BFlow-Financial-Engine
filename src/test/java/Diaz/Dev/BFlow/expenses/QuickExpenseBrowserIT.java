package Diaz.Dev.BFlow.expenses;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.repository.RepositoryUser;
import bflow.budget.repository.RepositoryBudget;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.income.entity.Income;
import bflow.subscription.services.SubscriptionService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Exercises the authenticated quick-expense customer flow through a real
 * HTTP server and Chromium, persisting its financial effects in PostgreSQL.
 */
@SpringBootTest(
        classes = bflow.BFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class QuickExpenseBrowserIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired private RepositoryUser users;
    @Autowired private RepositoryWallet wallets;
    @Autowired private RepositoryWalletUser memberships;
    @Autowired private RepositoryExpense expenses;
    @Autowired private RepositoryIncome incomes;
    @Autowired private RepositoryCategory categories;
    @Autowired private RepositoryBudget budgets;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private EntityManager entityManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UUID userId;
    private UUID walletId;
    private UUID expenseCategoryId;
    private UUID incomeCategoryId;
    private String token;
    private String subject;

    @BeforeEach
    void seedCustomerAndDefaultWallet() {
        token = UUID.randomUUID().toString();
        subject = UUID.randomUUID().toString();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation ->
                Jwt.withTokenValue(invocation.getArgument(0))
                        .header("alg", "test")
                        .subject(subject)
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build());

        User user = new User();
        user.setEmail("quick-expense-e2e+" + UUID.randomUUID()
                + "@example.test");
        user.setName("Browser E2E");
        user.setCognitoSub(subject);
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setLanguage(SupportedLanguage.EN);
        userId = users.saveAndFlush(user).getId();
        subscriptionService.createFreeSubscription(user);

        Category expenseCategory = new Category();
        expenseCategory.setName("E2E Groceries " + UUID.randomUUID());
        expenseCategory.setType(CategoryType.EXPENSE);
        expenseCategory.setSystemDefined(true);
        expenseCategoryId = categories.saveAndFlush(expenseCategory).getId();

        Category incomeCategory = new Category();
        incomeCategory.setName("E2E Salary " + UUID.randomUUID());
        incomeCategory.setType(CategoryType.INCOME);
        incomeCategory.setSystemDefined(true);
        incomeCategoryId = categories.saveAndFlush(incomeCategory).getId();

        Wallet wallet = new Wallet();
        wallet.setName("E2E wallet");
        wallet.setDescription("Browser test wallet");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(new BigDecimal("100.00"));
        wallet.setInitialValue(new BigDecimal("100.00"));
        walletId = wallets.saveAndFlush(wallet).getId();

        WalletUser membership = new WalletUser();
        membership.setUser(user);
        membership.setWallet(wallet);
        membership.setRole(WalletRole.OWNER);
        membership.setDefaultWallet(true);
        memberships.saveAndFlush(membership);
    }

    @Test
    void customerCreatesQuickExpenseAndWalletBalanceIsDebited() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                Page page = browser.newPage();
                page.navigate("http://localhost:" + port + "/swagger-ui/");

                String response = (String) page.evaluate("""
                        async (token) => {
                          const response = await fetch(
                            '/api/v1/expenses/quick', {
                              method: 'POST',
                              headers: {
                                'Authorization': `Bearer ${token}`,
                                'Content-Type': 'application/json',
                                'Idempotency-Key': crypto.randomUUID()
                              },
                              body: JSON.stringify({ amount: 25.00 })
                            });
                          return JSON.stringify({
                            status: response.status,
                            body: await response.json()
                          });
                        }
                        """, token);

                assertTrue(response.contains("\"status\":200"), response);
                assertTrue(response.contains("Quick expense"), response);
                assertTrue(response.contains("\"amount\":25"), response);

                int rejectedStatus = (int) page.evaluate("""
                        async (token) => {
                          const response = await fetch(
                            '/api/v1/expenses/quick', {
                              method: 'POST',
                              headers: {
                                'Authorization': `Bearer ${token}`,
                                'Content-Type': 'application/json',
                                'Idempotency-Key': crypto.randomUUID()
                              },
                              body: JSON.stringify({ amount: 0 })
                            });
                          return response.status;
                        }
                        """, token);
                assertEquals(400, rejectedStatus);
            } finally {
                browser.close();
            }
        }

        entityManager.clear();
        Wallet persistedWallet = wallets.findById(walletId).orElseThrow();
        assertEquals(0, new BigDecimal("75.00")
                .compareTo(persistedWallet.getBalance()));
        assertEquals(1, expenses.countByWalletId(walletId));
    }

    @Test
    void customerCreatesWalletBudgetThroughAuthenticatedBrowserSession() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                Page page = browser.newPage();
                page.navigate("http://localhost:" + port + "/swagger-ui/");

                String response = (String) page.evaluate("""
                        async ({token, walletId}) => {
                          const response = await fetch('/api/v1/budgets', {
                            method: 'POST',
                            headers: {
                              'Authorization': `Bearer ${token}`,
                              'Content-Type': 'application/json',
                              'Idempotency-Key': crypto.randomUUID()
                            },
                            body: JSON.stringify({
                              walletId,
                              amount: 200.00,
                              period: 'MONTHLY',
                              startDate: new Date(Date.now() - 2 * 86400000)
                                .toISOString().slice(0, 10),
                              scope: 'WALLET',
                              currency: 'USD'
                            })
                          });
                          return JSON.stringify({
                            status: response.status,
                            body: await response.text()
                          });
                        }
                        """, java.util.Map.of(
                        "token", token, "walletId", walletId.toString()));

                assertTrue(response.contains("\"status\":201"), response);
            } finally {
                browser.close();
            }
        }

        entityManager.clear();
        assertEquals(1, budgets.countByUserId(userId));
    }

    @Test
    void customerReadsWalletBalanceAfterQuickExpenseInBrowser() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                Page page = browser.newPage();
                page.navigate("http://localhost:" + port + "/swagger-ui/");

                String response = (String) page.evaluate("""
                        async ({token, walletId}) => {
                          const headers = {
                            'Authorization': `Bearer ${token}`,
                            'Content-Type': 'application/json',
                            'Idempotency-Key': crypto.randomUUID()
                          };
                          const expense = await fetch('/api/v1/expenses/quick', {
                            method: 'POST', headers,
                            body: JSON.stringify({ amount: 12.50 })
                          });
                          const wallet = await fetch(
                            `/api/v1/wallets/${walletId}`, { headers });
                          return JSON.stringify({
                            expenseStatus: expense.status,
                            walletStatus: wallet.status,
                            wallet: await wallet.text()
                          });
                        }
                        """, java.util.Map.of(
                        "token", token, "walletId", walletId.toString()));

                assertTrue(response.contains("\"expenseStatus\":200"), response);
                assertTrue(response.contains("\"walletStatus\":200"), response);
                assertTrue(response.contains("87.50"), response);
            } finally {
                browser.close();
            }
        }

        entityManager.clear();
        assertEquals(1, expenses.countByWalletId(walletId));
        assertEquals(0, new BigDecimal("87.50").compareTo(
                wallets.findById(walletId).orElseThrow().getBalance()));
    }

    @Test
    void customerCreatesNormalExpenseAndPersistsCategoryAndDebitedBalance() {
        String transactionDate = LocalDate.now().minusDays(1).toString();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                Page page = browser.newPage();
                page.navigate("http://localhost:" + port + "/swagger-ui/");
                String response = (String) page.evaluate("""
                        async ({token, walletId, categoryId, date}) => {
                          const response = await fetch('/api/v1/expenses', {
                            method: 'POST',
                            headers: {
                              'Authorization': `Bearer ${token}`,
                              'Content-Type': 'application/json',
                              'Idempotency-Key': crypto.randomUUID()
                            },
                            body: JSON.stringify({
                              walletId,
                              categoryId,
                              title: 'Groceries at Market',
                              description: 'Weekly food shopping',
                              amount: 30.00,
                              date,
                              source: 'manual',
                              recurring: false
                            })
                          });
                          return JSON.stringify({
                            status: response.status,
                            body: await response.json()
                          });
                        }
                        """, java.util.Map.of(
                        "token", token,
                        "walletId", walletId.toString(),
                        "categoryId", expenseCategoryId.toString(),
                        "date", transactionDate));
                assertTrue(response.contains("\"status\":201"), response);
                assertTrue(response.contains("Groceries at Market"), response);
                assertTrue(response.contains("\"amount\":30"), response);
            } finally {
                browser.close();
            }
        }

        entityManager.clear();
        Expense persisted = expenses.findByWalletId(
                walletId, PageRequest.of(0, 5)).getContent().get(0);
        assertEquals("Groceries at Market", persisted.getTitle());
        assertEquals(expenseCategoryId, persisted.getCategory().getId());
        assertEquals(new BigDecimal("30.00"), persisted.getAmount());
        assertEquals(0, new BigDecimal("70.00").compareTo(
                wallets.findById(walletId).orElseThrow().getBalance()));
    }

    @Test
    void customerCreatesIncomeAndPersistsCategoryAndCreditedBalance() {
        String transactionDate = LocalDate.now().minusDays(1).toString();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                Page page = browser.newPage();
                page.navigate("http://localhost:" + port + "/swagger-ui/");
                String response = (String) page.evaluate("""
                        async ({token, walletId, categoryId, date}) => {
                          const response = await fetch('/api/v1/incomes', {
                            method: 'POST',
                            headers: {
                              'Authorization': `Bearer ${token}`,
                              'Content-Type': 'application/json',
                              'Idempotency-Key': crypto.randomUUID()
                            },
                            body: JSON.stringify({
                              walletId,
                              categoryId,
                              title: 'Salary deposit',
                              description: 'Monthly payroll',
                              amount: 250.00,
                              date,
                              source: 'manual',
                              recurring: false
                            })
                          });
                          return JSON.stringify({
                            status: response.status,
                            body: await response.json()
                          });
                        }
                        """, java.util.Map.of(
                        "token", token,
                        "walletId", walletId.toString(),
                        "categoryId", incomeCategoryId.toString(),
                        "date", transactionDate));
                assertTrue(response.contains("\"status\":201"), response);
                assertTrue(response.contains("Salary deposit"), response);
                assertTrue(response.contains("\"amount\":250"), response);
            } finally {
                browser.close();
            }
        }

        entityManager.clear();
        Income persisted = incomes.findByWalletId(
                walletId, PageRequest.of(0, 5)).getContent().get(0);
        assertEquals("Salary deposit", persisted.getTitle());
        assertEquals(incomeCategoryId, persisted.getCategory().getId());
        assertEquals(new BigDecimal("250.00"), persisted.getAmount());
        assertEquals(0, new BigDecimal("350.00").compareTo(
                wallets.findById(walletId).orElseThrow().getBalance()));
    }
}
