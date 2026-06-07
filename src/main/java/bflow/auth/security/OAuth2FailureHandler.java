package bflow.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handler for OAuth2 authentication failures.
 * Redirects to the frontend login page when OAuth2 authentication fails.
 */
@Component
public final class OAuth2FailureHandler
        implements AuthenticationFailureHandler {

    /** The frontend URL for redirecting on authentication failure. */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Handles OAuth2 authentication failure by redirecting to the frontend
     * login page.
     * @param request the HTTP servlet request.
     * @param response the HTTP servlet response.
     * @param exception the authentication exception.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void onAuthenticationFailure(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException exception
    ) throws IOException {
        String redirectUrl = frontendUrl + "/auth/login?error=oauth2";
        response.sendRedirect(redirectUrl);
    }
}
