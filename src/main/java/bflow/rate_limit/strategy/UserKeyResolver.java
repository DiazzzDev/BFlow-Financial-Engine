package bflow.rate_limit.strategy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

        final Object principal = authentication.getPrincipal();

        /*
         * Replace this with your actual JWT principal implementation.
         */
        if (principal instanceof String userId) {
            return "user:" + userId;
        }

        return null;
    }
}
