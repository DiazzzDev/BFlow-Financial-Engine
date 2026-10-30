package Diaz.Dev.BFlow.rate_limit.policy;

import bflow.rate_limit.policy.EndpointPolicyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointPolicyResolverTest {

    private final EndpointPolicyResolver resolver =
            new EndpointPolicyResolver();

    @Test
    void sensitiveAuthenticationEndpointsUseTheirDedicatedPolicies() {
        assertEquals("LOGIN", resolve("/api/auth/login"));
        assertEquals("REGISTER", resolve("/api/auth/register"));
        assertEquals("REGISTER", resolve("/api/auth/register/confirm"));
        assertEquals("FORGOT_PASSWORD",
                resolve("/api/auth/forgot-password"));
        assertEquals("RESEND_VERIFICATION",
                resolve("/api/auth/resend-verification"));
        assertEquals("VERIFY_EMAIL", resolve("/api/auth/verify-email"));
    }

    @Test
    void otherApplicationEndpointsUseTheGeneralApiPolicy() {
        assertEquals("AUTHENTICATED_API", resolve("/api/v1/wallets"));
        assertEquals("AUTHENTICATED_API", resolve("/health"));
    }

    @Test
    void documentationAndOperationalResourcesBypassRateLimiting() {
        assertTrue(resolver.shouldSkip("/swagger-ui/index.html"));
        assertTrue(resolver.shouldSkip("/v3/api-docs"));
        assertTrue(resolver.shouldSkip("/actuator/health"));
        assertTrue(resolver.shouldSkip("/static/logo.svg"));
        assertFalse(resolver.shouldSkip("/api/v1/wallets"));
    }

    private String resolve(final String path) {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).setRequestURI(path);
        return resolver.resolve(request);
    }
}
