package bflow.rate_limit.strategy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UserKeyResolver implements KeyResolver {

    @Override
    public String resolve(HttpServletRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        /*
         * Replace this with your actual JWT principal implementation.
         */
        if (principal instanceof String userId) {
            return "user:" + userId;
        }

        return null;
    }
}