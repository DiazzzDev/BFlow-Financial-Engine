package Diaz.Dev.BFlow.auth.security;

import bflow.auth.entities.User;
import bflow.auth.enums.AuthProvider;
import bflow.auth.security.OAuth2SuccessHandler;
import bflow.auth.security.jwt.JwtService;
import bflow.auth.services.ServiceRefreshToken;
import bflow.auth.services.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class OAuth2SuccessHandlerTest {

    @Mock
    JwtService jwtService;

    @Mock
    UserService userService;

    @Mock
    ServiceRefreshToken serviceRefreshToken;

    OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);

        handler = new OAuth2SuccessHandler(
                jwtService,
                userService,
                serviceRefreshToken,
                "http://localhost:3000"
        );
    }

    @Test
    void redirectsSuccessfullyAfterOAuthLogin() throws Exception {

        OAuth2User user = new OAuth2User() {

            @Override
            public Map<String, Object> getAttributes() {

                return Map.of(
                        "email", "a@b.com",
                        "sub", "prov-1",
                        "email_verified", true
                );
            }

            @Override
            public List getAuthorities() {
                return List.of();
            }

            @Override
            public String getName() {
                return "a@b.com";
            }
        };

        Authentication auth = new Authentication() {

            @Override
            public List getAuthorities() {
                return List.of();
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return user;
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(
                final boolean isAuthenticated
            ) throws IllegalArgumentException {
                throw new UnsupportedOperationException(
                "Not required for this test"
                );
            }

            @Override
            public String getName() {
                return "a@b.com";
            }
        };

        User u = new User();

        u.setId(UUID.randomUUID());
        u.setEmail("a@b.com");
        u.setRoles(Set.of("ROLE_USER"));

        Boolean emailVerified =
                (Boolean) user.getAttributes().get("email_verified");

        when(
                userService.resolveOAuth2User(
                        "a@b.com",
                        "a@b.com",
                        AuthProvider.GOOGLE,
                        Boolean.TRUE.equals(emailVerified)
                )
        ).thenReturn(u);

        when(
                jwtService.generateToken(
                        u.getId(),
                        u.getEmail(),
                        List.of("ROLE_USER")
                )
        ).thenReturn("jwt-token");

        doNothing().when(serviceRefreshToken)
                .create(
                        org.mockito.ArgumentMatchers.any(),
                        anyString()
                );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                null,
                response,
                auth
        );

        assertEquals(302, response.getStatus());
    }
}