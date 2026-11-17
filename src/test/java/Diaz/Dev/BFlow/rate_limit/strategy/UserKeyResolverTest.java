package Diaz.Dev.BFlow.rate_limit.strategy;

import bflow.rate_limit.strategy.UserKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserKeyResolverTest {

    private final UserKeyResolver resolver = new UserKeyResolver();
    private final HttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesJwtSubjectForDefaultSpringResourceServerAuthentication() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("cognito-user-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES)
        );

        assertEquals("user:cognito-user-subject", resolver.resolve(request));
    }

    @Test
    void preservesStringPrincipalSupport() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "legacy-user", "n/a", AuthorityUtils.NO_AUTHORITIES)
        );

        assertEquals("user:legacy-user", resolver.resolve(request));
    }

    @Test
    void returnsNullWithoutAuthenticatedIdentity() {
        assertNull(resolver.resolve(request));
    }
}
