package Diaz.Dev.BFlow.auth.services;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.CurrentUserService;
import bflow.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock private RepositoryUser repositoryUser;
    @Mock private Authentication authentication;
    @InjectMocks private CurrentUserService service;

    @Test
    void getCurrentUser_resolvesUserByJwtSubject() {
        String cognitoSubject = "cognito-user-123";
        Jwt jwt = jwt(cognitoSubject);
        User user = new User();
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(repositoryUser.findByCognitoSub(cognitoSubject))
                .thenReturn(Optional.of(user));

        assertSame(user, service.getCurrentUser(authentication));
        verify(repositoryUser).findByCognitoSub(cognitoSubject);
    }

    @Test
    void getCurrentUser_throwsWhenJwtSubjectHasNoUser() {
        when(authentication.getPrincipal()).thenReturn(jwt("missing"));
        when(repositoryUser.findByCognitoSub("missing"))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.getCurrentUser(authentication));
    }

    @Test
    void getCurrentUserId_returnsResolvedUserId() {
        String cognitoSubject = "cognito-user-456";
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(authentication.getPrincipal()).thenReturn(jwt(cognitoSubject));
        when(repositoryUser.findByCognitoSub(cognitoSubject))
                .thenReturn(Optional.of(user));

        assertEquals(userId, service.getCurrentUserId(authentication));
    }

    private Jwt jwt(final String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
