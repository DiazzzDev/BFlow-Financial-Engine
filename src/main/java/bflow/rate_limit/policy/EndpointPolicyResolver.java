package bflow.rate_limit.policy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class EndpointPolicyResolver {
    public String resolve(HttpServletRequest request) {

        String path = request.getRequestURI();

        if (path.equals("/api/auth/login")) {
            return "LOGIN";
        }

        if (path.startsWith("/api/auth/register")) {
            return "REGISTER";
        }

        if (path.startsWith("/api/auth/forgot-password")) {
            return "FORGOT_PASSWORD";
        }

        return "AUTHENTICATED_API";
    }

    public boolean shouldSkip(String path) {

        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator")
                || path.startsWith("/static");
    }
}
