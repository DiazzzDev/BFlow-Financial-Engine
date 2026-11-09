package Diaz.Dev.BFlow.gmail;

import bflow.auth.repository.RepositoryUser;
import bflow.gmail.repository.GmailIntegrationRepository;
import bflow.gmail.repository.GmailOAuthStateRepository;
import bflow.gmail.service.GmailOAuthService;
import bflow.gmail.service.GmailTokenCipher;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailOAuthServiceTest {
    @Test
    void invalidOAuthStateDoesNotExchangeCodeOrStoreCredentials() {
        GmailOAuthStateRepository stateRepository = mock(GmailOAuthStateRepository.class);
        GmailIntegrationRepository integrationRepository = mock(GmailIntegrationRepository.class);
        RepositoryUser userRepository = mock(RepositoryUser.class);
        GmailTokenCipher cipher = mock(GmailTokenCipher.class);
        RepositoryWalletUser walletUserRepository = mock(RepositoryWalletUser.class);
        when(stateRepository.consumeValid(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        GmailOAuthService service = new GmailOAuthService(stateRepository,
                integrationRepository, userRepository, cipher, walletUserRepository);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.example/");

        String redirect = service.completeCallback("authorization-code", "forged-state");

        assertThat(redirect).isEqualTo("https://app.example/settings?gmail=invalid_oauth");
        verify(integrationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).getReferenceById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingCallbackParametersRedirectToSafeErrorAndTrimTrailingSlashes() {
        GmailOAuthService service = service(mock(GmailOAuthStateRepository.class),
                mock(GmailIntegrationRepository.class), mock(RepositoryUser.class),
                mock(GmailTokenCipher.class), mock(RepositoryWalletUser.class));
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.example///");

        assertThat(service.completeCallback(null, "oauth-state"))
                .isEqualTo("https://app.example/settings?gmail=error");
        assertThat(service.settingsUrl("connected"))
                .isEqualTo("https://app.example/settings?gmail=connected");
    }

    @Test
    void refusesAuthorizationWhenClientCredentialsAreNotConfigured() {
        GmailOAuthService service = service(mock(GmailOAuthStateRepository.class),
                mock(GmailIntegrationRepository.class), mock(RepositoryUser.class),
                mock(GmailTokenCipher.class), mock(RepositoryWalletUser.class));
        ReflectionTestUtils.setField(service, "clientId", "");
        ReflectionTestUtils.setField(service, "clientSecret", "");

        assertThatThrownBy(() -> service.createAuthorizationUrl(java.util.UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gmail OAuth is not configured");
    }

    private GmailOAuthService service(GmailOAuthStateRepository states,
            GmailIntegrationRepository integrations, RepositoryUser users,
            GmailTokenCipher cipher, RepositoryWalletUser walletUsers) {
        return new GmailOAuthService(states, integrations, users, cipher, walletUsers);
    }
}
