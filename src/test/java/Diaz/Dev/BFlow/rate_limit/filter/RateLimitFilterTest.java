package Diaz.Dev.BFlow.rate_limit.filter;

import bflow.rate_limit.policy.EndpointPolicyResolver;
import bflow.rate_limit.policy.RateLimitPolicy;
import bflow.rate_limit.policy.RateLimitPolicyRegistry;
import bflow.rate_limit.filter.RateLimitFilter;
import bflow.rate_limit.service.RateLimitService;
import bflow.rate_limit.strategy.IpKeyResolver;
import bflow.rate_limit.strategy.UserKeyResolver;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private EndpointPolicyResolver endpointPolicyResolver;

    @Mock
    private RateLimitPolicyRegistry policyRegistry;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private IpKeyResolver ipKeyResolver;

    @Mock
    private UserKeyResolver userKeyResolver;

    @Mock
    private FilterChain filterChain;

    @Mock
    private ConsumptionProbe probe;

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new RateLimitFilter(
                endpointPolicyResolver, policyRegistry, rateLimitService,
                ipKeyResolver, userKeyResolver);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/transactions");
        request.setRemoteAddr("203.0.113.8");
        response = new MockHttpServletResponse();
        policy = new RateLimitPolicy(10, Duration.ofMinutes(1));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void documentationRequestBypassesRateLimitAndContinues() throws Exception {
        request.setRequestURI("/swagger-ui/index.html");
        when(endpointPolicyResolver.shouldSkip(request.getRequestURI()))
                .thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(
                policyRegistry, rateLimitService, ipKeyResolver, userKeyResolver);
    }

    @Test
    void endpointWithoutPolicyContinuesWithoutConsumingAToken()
            throws Exception {
        when(endpointPolicyResolver.shouldSkip(request.getRequestURI()))
                .thenReturn(false);
        when(endpointPolicyResolver.resolve(request)).thenReturn("UNKNOWN");
        when(policyRegistry.getPolicy("UNKNOWN")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimitService, ipKeyResolver, userKeyResolver);
    }

    @Test
    void anonymousRequestUsesClientIpAndReturnsRemainingCapacity()
            throws Exception {
        allowRequestWithPolicy();
        when(ipKeyResolver.resolve(request)).thenReturn("ip:203.0.113.8");
        when(rateLimitService.tryConsume("ip:203.0.113.8", policy))
                .thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(7L);

        filter.doFilter(request, response, filterChain);

        assertEquals("7", response.getHeader("X-Rate-Limit-Remaining"));
        verify(filterChain).doFilter(request, response);
        verify(ipKeyResolver).resolve(request);
        verify(userKeyResolver, never()).resolve(request);
    }

    @Test
    void authenticatedRequestUsesUserBucketInsteadOfSharedIpBucket()
            throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "customer", "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        allowRequestWithPolicy();
        when(userKeyResolver.resolve(request)).thenReturn("user:42");
        when(rateLimitService.tryConsume("user:42", policy))
                .thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(4L);

        filter.doFilter(request, response, filterChain);

        assertEquals("4", response.getHeader("X-Rate-Limit-Remaining"));
        verify(userKeyResolver).resolve(request);
        verify(ipKeyResolver, never()).resolve(request);
        verify(rateLimitService).tryConsume("user:42", policy);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedRequestWithoutUserKeyFallsBackToClientIp()
            throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "customer", "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        allowRequestWithPolicy();
        when(userKeyResolver.resolve(request)).thenReturn(null);
        when(ipKeyResolver.resolve(request)).thenReturn("ip:203.0.113.8");
        when(rateLimitService.tryConsume("ip:203.0.113.8", policy))
                .thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(2L);

        filter.doFilter(request, response, filterChain);

        assertEquals("2", response.getHeader("X-Rate-Limit-Remaining"));
        verify(userKeyResolver).resolve(request);
        verify(ipKeyResolver).resolve(request);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void exhaustedBucketReturns429RetryTimeAndDoesNotContinue()
            throws Exception {
        allowRequestWithPolicy();
        when(ipKeyResolver.resolve(request)).thenReturn("ip:203.0.113.8");
        when(rateLimitService.tryConsume("ip:203.0.113.8", policy))
                .thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(2_500_000_000L);

        filter.doFilter(request, response, filterChain);

        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals("0", response.getHeader("X-Rate-Limit-Remaining"));
        assertEquals("2", response.getHeader("X-Rate-Limit-Retry-After"));
        assertTrue(response.getContentAsString()
                .contains("Rate limit exceeded"));
        verify(filterChain, never()).doFilter(request, response);
    }

    private void allowRequestWithPolicy() {
        when(endpointPolicyResolver.shouldSkip(request.getRequestURI()))
                .thenReturn(false);
        when(endpointPolicyResolver.resolve(request)).thenReturn("API");
        when(policyRegistry.getPolicy("API")).thenReturn(policy);
    }
}
