package bflow.gmail.service;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.gmail.entity.GmailIntegration;
import bflow.gmail.entity.GmailOAuthState;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.gmail.repository.GmailOAuthStateRepository;
import bflow.wallet.repository.RepositoryWalletUser;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailOAuthService {
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final NetHttpTransport TRANSPORT = new NetHttpTransport();

    private final GmailOAuthStateRepository stateRepository;
    private final GmailIntegrationRepository integrationRepository;
    private final RepositoryUser userRepository;
    private final GmailTokenCipher tokenCipher;
    private final RepositoryWalletUser walletUserRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${aws.gmail.client-id:}") private String clientId;
    @Value("${aws.gmail.client-secret:}") private String clientSecret;
    @Value("${app.base-url}") private String baseUrl;
    @Value("${app.frontend-url}") private String frontendUrl;

    @Transactional
    public String createAuthorizationUrl(final UUID userId) {
        ensureConfigured();
        byte[] entropy = new byte[32];
        secureRandom.nextBytes(entropy);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        GmailOAuthState oauthState = new GmailOAuthState();
        oauthState.setStateHash(sha256(state));
        oauthState.setUser(userRepository.getReferenceById(userId));
        oauthState.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        stateRepository.save(oauthState);
        return flow().newAuthorizationUrl().setRedirectUri(redirectUri())
                .setState(state).set("prompt", "consent").build();
    }

    @Transactional
    public String completeCallback(final String code, final String state) {
        if (state == null || code == null || code.isBlank()) return settingsUrl("error");
        String stateHash = sha256(state);
        if (stateRepository.consumeValid(stateHash, Instant.now()) != 1) {
            return settingsUrl("invalid_oauth");
        }
        GmailOAuthState oauthState = stateRepository.findById(stateHash).orElse(null);
        if (oauthState == null) return settingsUrl("invalid_oauth");
        String step = "exchange_code";
        try {
            GoogleTokenResponse tokens = flow().newTokenRequest(code)
                    .setRedirectUri(redirectUri()).execute();
            if (tokens.getRefreshToken() == null || tokens.getRefreshToken().isBlank()) {
                return settingsUrl("missing_refresh_token");
            }
            User user = oauthState.getUser();
            GmailIntegration integration = integrationRepository.findByUserId(user.getId())
                    .orElseGet(GmailIntegration::new);
            integration.setUser(user);
            step = "encrypt_refresh_token";
            integration.setEncryptedRefreshToken(tokenCipher.encrypt(tokens.getRefreshToken()));
            integration.setStatus("CONNECTED");
            walletUserRepository.findFirstByUserIdOrderByDefaultWalletDescCreatedAtAsc(user.getId())
                    .ifPresent(membership -> integration.setWalletId(membership.getWallet().getId()));
            step = "persist_integration";
            integrationRepository.save(integration);
            return settingsUrl("connected");
        } catch (IOException | RuntimeException failure) {
            log.warn("Gmail OAuth callback failed at step {} ({})", step,
                    failure.getClass().getSimpleName());
            return settingsUrl("oauth_error");
        }
    }

    @Transactional(readOnly = true)
    public String accessToken(final GmailIntegration integration) {
        ensureConfigured();
        Credential credential = refreshCredential();
        credential.setRefreshToken(tokenCipher.decrypt(integration.getEncryptedRefreshToken()));
        try {
            if (!credential.refreshToken() || credential.getAccessToken() == null) {
                throw new IllegalStateException("Gmail authorization was revoked");
            }
            return credential.getAccessToken();
        } catch (IOException failure) {
            String message = failure.getMessage();
            if (message != null && message.contains("invalid_grant")) {
                throw new IllegalStateException("Gmail authorization was revoked");
            }
            throw new IllegalStateException("Gmail token refresh failed");
        }
    }

    public void revoke(final String refreshToken) {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                TRANSPORT.createRequestFactory().buildPostRequest(
                        new GenericUrl("https://oauth2.googleapis.com/revoke"),
                        new UrlEncodedContent(Map.of("token", refreshToken)))
                        .execute().disconnect();
                return;
            } catch (IOException failure) {
                lastFailure = failure;
                if (attempt < 3) {
                    try {
                        Thread.sleep(250L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Gmail token revocation interrupted", interrupted);
                    }
                }
            }
        }
        throw new IllegalStateException("Gmail token revocation failed", lastFailure);
    }

    public String settingsUrl(final String result) {
        return trimTrailingSlashes(frontendUrl) + "/settings?gmail=" + result;
    }

    private GoogleAuthorizationCodeFlow flow() {
        return new GoogleAuthorizationCodeFlow.Builder(TRANSPORT, JSON_FACTORY,
                clientId, clientSecret, List.of(GmailScopes.GMAIL_READONLY))
                .setAccessType("offline").build();
    }

    private Credential refreshCredential() {
        return new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(TRANSPORT).setJsonFactory(JSON_FACTORY)
                .setTokenServerUrl(new GenericUrl(GoogleOAuthConstants.TOKEN_SERVER_URL))
                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
                .build();
    }

    private String redirectUri() {
        return trimTrailingSlashes(baseUrl) + "/api/v1/integrations/gmail/callback";
    }

    private String trimTrailingSlashes(final String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    private void ensureConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("Gmail OAuth is not configured");
        }
    }

    private static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

}
