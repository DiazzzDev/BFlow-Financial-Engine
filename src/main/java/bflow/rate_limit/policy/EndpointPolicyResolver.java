package bflow.rate_limit.policy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public final class EndpointPolicyResolver {
    /**
     * Resolves the rate limiting policy key for a given request.
     * @param request the HTTP request to resolve the policy for.
     * @return the policy key for rate limiting.
     */
    public String resolve(final HttpServletRequest request) {

        final String path = request.getRequestURI();

        if (path.equals("/api/auth/login")) {
            return "LOGIN";
        }

        if (path.startsWith("/api/auth/register")) {
            return "REGISTER";
        }

        if (path.startsWith("/api/auth/forgot-password")) {
            return "FORGOT_PASSWORD";
        }

        if (path.startsWith("/api/auth/resend-verification")) {
            return "RESEND_VERIFICATION";
        }

        if (path.startsWith("/api/auth/verify-email")) {
            return "VERIFY_EMAIL";
        }

        return "AUTHENTICATED_API";
    }

    /**
     * Determines if rate limiting should be skipped for the given path.
     * @param path the request path to check.
     * @return true if rate limiting should be skipped, false otherwise.
     */
    public boolean shouldSkip(final String path) {

        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator")
                || path.startsWith("/static");
    }
}
