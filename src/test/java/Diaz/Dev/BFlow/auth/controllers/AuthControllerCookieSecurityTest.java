package Diaz.Dev.BFlow.auth.controllers;

import bflow.auth.DTO.AuthLoginRequest;
import bflow.auth.entities.User;
import bflow.auth.security.jwt.JwtService;
import bflow.auth.services.AuthService;
import bflow.auth.services.ServiceRefreshToken;
import bflow.auth.controllers.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.http.HttpServletResponse;

class AuthControllerCookieSecurityTest {

    @Mock
    AuthService authService;
    @Mock
    JwtService jwtService;
    @Mock
    ServiceRefreshToken serviceRefreshToken;

    @InjectMocks
    AuthController controller;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void loginSetsInsecureCookieFlags() {
        AuthLoginRequest req = new AuthLoginRequest();
        req.setEmail("a@b.com");
        req.setPassword("pass");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("a@b.com");

        when(authService.authenticate(req.getEmail(), req.getPassword()))
                .thenReturn(user);
        when(authService.getRoles(user)).thenReturn(List.of("USER"));
        when(jwtService.generateToken(user.getId(), user.getEmail(),
                List.of("USER"))).thenReturn("token123");

        // Mock serviceRefreshToken.create to do nothing
        doAnswer((InvocationOnMock invocation) -> null).when(
                serviceRefreshToken).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());

        // Mock attachAuthCookies to actually set cookies
        doAnswer((InvocationOnMock invocation) -> {
            HttpServletResponse response =
                    invocation.getArgument(0);
            String accessToken = invocation.getArgument(1);
            String refreshToken = invocation.getArgument(2);

            response.addHeader("Set-Cookie",
                    "access_token=" + accessToken
                            + "; Path=/; SameSite=None");
            response.addHeader("Set-Cookie",
                    "refresh_token=" + refreshToken
                            + "; Path=/; SameSite=None");
            return null;
        }).when(jwtService).attachAuthCookies(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );

        MockHttpServletResponse res =
                new MockHttpServletResponse();

        controller.login(req, res);

        var headers = res.getHeaders("Set-Cookie");
        assertTrue(headers.stream()
                .anyMatch(h -> h.contains("access_token")));
        assertTrue(headers.stream()
                .anyMatch(h -> h.contains("refresh_token")));

        // Current implementation: insecure flags
        assertTrue(headers.stream()
                .anyMatch(h -> h.contains("SameSite=None")));
    }
}
