package Diaz.Dev.BFlow.auth;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import bflow.wallet.repository.RepositoryWalletUser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Exercises the Cognito-to-application account lifecycle through the real
 * HTTP security filter chain and browser client. Cognito token decoding is
 * represented by decoder fixtures because CI has no AWS user-pool account.
 */
@SpringBootTest(
        classes = bflow.BFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class AuthLifecycleBrowserIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired private RepositoryUser users;
    @Autowired private RepositoryWalletUser walletUsers;
    @Autowired private RepositorySubscription subscriptions;

    /** JWT decoder used by Spring Security and the ID-token validator. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final Map<String, Jwt> cognitoTokens = new ConcurrentHashMap<>();

    private String cognitoSubject;
    private String email;
    private String mismatchedEmail;
    private String accessToken;
    private String idToken;
    private String mismatchedIdToken;

    @BeforeEach
    void prepareCognitoTokenFixtures() {
        cognitoTokens.clear();

        cognitoSubject = UUID.randomUUID().toString();
        email = "cognito-auth-e2e+" + UUID.randomUUID() + "@example.test";
        mismatchedEmail = "cognito-mismatch+" + UUID.randomUUID()
                + "@example.test";
        accessToken = "access-" + UUID.randomUUID();
        idToken = "id-" + UUID.randomUUID();
        mismatchedIdToken = "id-mismatch-" + UUID.randomUUID();

        Instant now = Instant.now();
        cognitoTokens.put(accessToken, Jwt.withTokenValue(accessToken)
                .header("alg", "RS256")
                .subject(cognitoSubject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build());
        cognitoTokens.put(idToken, cognitoIdToken(
                idToken, cognitoSubject, email, "Cognito browser user"));
        cognitoTokens.put(mismatchedIdToken, cognitoIdToken(
                mismatchedIdToken, UUID.randomUUID().toString(),
                mismatchedEmail, "Wrong subject"));

        when(jwtDecoder.decode(anyString())).thenAnswer(invocation ->
                cognitoTokens.get(invocation.getArgument(0)));
    }

    @Test
    void firstSignInBootstrapsAccountAndRepeatedSignInIsIdempotent() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                Page page = browser.newPage();
                page.navigate("http://localhost:" + port + "/swagger-ui/");
                String result = (String) page.evaluate("""
                        async ({accessToken, idToken, mismatchedIdToken}) => {
                          const sync = async (token) => {
                            const response = await fetch('/api/v1/auth/sync', {
                              method: 'POST',
                              headers: {
                                'Authorization': `Bearer ${accessToken}`,
                                'Content-Type': 'application/json'
                              },
                              body: JSON.stringify({ idToken: token })
                            });
                            return {
                              status: response.status,
                              body: await response.json()
                            };
                          };
                          return JSON.stringify({
                            first: await sync(idToken),
                            repeat: await sync(idToken),
                            mismatch: await sync(mismatchedIdToken)
                          });
                        }
                        """, Map.of(
                        "accessToken", accessToken,
                        "idToken", idToken,
                        "mismatchedIdToken", mismatchedIdToken));

                assertTrue(result.contains("\"status\":200"), result);
                assertTrue(result.contains("\"isNewUser\":true"), result);
                assertTrue(result.contains("\"isNewUser\":false"), result);
                assertTrue(result.contains("\"mismatch\":{\"status\":404"),
                        result);
            } finally {
                browser.close();
            }
        }

        User persisted = users.findByCognitoSub(cognitoSubject).orElseThrow();
        assertEquals(email, persisted.getEmail());
        assertEquals("Cognito browser user", persisted.getName());
        assertTrue(persisted.isEmailVerified());
        assertEquals(1, walletUsers.findByUserId(persisted.getId()).size());
        assertTrue(subscriptions.findByUserIdAndStatus(
                persisted.getId(), SubscriptionStatus.ACTIVE).isPresent());

        assertFalse(users.findByEmail(mismatchedEmail).isPresent());
    }

    private Jwt cognitoIdToken(
            final String token,
            final String subject,
            final String tokenEmail,
            final String name
    ) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("email", tokenEmail)
                .claim("email_verified", true)
                .claim("name", name)
                .build();
    }
}
