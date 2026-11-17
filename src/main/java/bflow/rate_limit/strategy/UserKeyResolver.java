package bflow.rate_limit.strategy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public final class UserKeyResolver implements KeyResolver {

    /**
     * Resolves a rate limiting key based on the authenticated user.
     * @param request the HTTP request containing authentication info.
     * @return a unique key for rate limiting based on user, or null if not
     * authenticated.
     */
    @Override
    public String resolve(final HttpServletRequest request) {

        final Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            final String subject = jwtAuthentication.getToken().getSubject();
            return subject == null || subject.isBlank()
                    ? null
                    : "user:" + subject;
        }

        final Object principal = authentication.getPrincipal();
        if (principal instanceof String userId) {
            return "user:" + userId;
        }

        return null;
    }
}
