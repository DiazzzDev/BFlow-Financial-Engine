package bflow.rate_limit.filter;
import bflow.rate_limit.policy.EndpointPolicyResolver;
import bflow.rate_limit.policy.RateLimitPolicy;
import bflow.rate_limit.policy.RateLimitPolicyRegistry;
import bflow.rate_limit.service.RateLimitService;
import bflow.rate_limit.strategy.IpKeyResolver;
import bflow.rate_limit.strategy.UserKeyResolver;
import bflow.rate_limit.util.RateLimitResponseUtil;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public final class RateLimitFilter extends OncePerRequestFilter {

    /** Nanoseconds to milliseconds conversion factor. */
    private static final long NANOS_TO_MILLIS = 1_000_000_000;

    /** Resolver for rate limiting policies based on endpoints. */
    private final EndpointPolicyResolver endpointPolicyResolver;
    /** Registry of rate limiting policies. */
    private final RateLimitPolicyRegistry policyRegistry;
    /** Service for rate limit consumption management. */
    private final RateLimitService rateLimitService;

    /** Resolver for IP-based rate limiting keys. */
    private final IpKeyResolver ipKeyResolver;
    /** Resolver for user-based rate limiting keys. */
    private final UserKeyResolver userKeyResolver;

    /**
     * Processes incoming requests to apply rate limiting based on
     * configured policies.
     * @param request the HTTP request.
     * @param response the HTTP response.
     * @param filterChain the filter chain to continue processing.
     * @throws ServletException if servlet processing fails.
     * @throws IOException if I/O fails.
     */
    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (endpointPolicyResolver.shouldSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String policyKey = endpointPolicyResolver.resolve(request);

        RateLimitPolicy policy = policyRegistry.getPolicy(policyKey);

        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = resolveBucketKey(request);

        ConsumptionProbe probe =
                rateLimitService.tryConsume(bucketKey, policy);

        if (!probe.isConsumed()) {

            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setHeader(
                    "X-Rate-Limit-Retry-After",
                    String.valueOf(
                            probe.getNanosToWaitForRefill() / NANOS_TO_MILLIS
                    )
            );

            RateLimitResponseUtil.writeExceededResponse(response);
            return;
        }

        response.setHeader(
                "X-Rate-Limit-Remaining",
                String.valueOf(probe.getRemainingTokens())
        );

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the bucket key for rate limiting based on the request.
     * @param request the HTTP request.
     * @return the bucket key for this request.
     */
    private String resolveBucketKey(final HttpServletRequest request) {

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        boolean authenticated =
                auth != null
                        && auth.isAuthenticated()
                        && !(auth instanceof AnonymousAuthenticationToken);

        if (authenticated) {
            String userKey = userKeyResolver.resolve(request);
            if (userKey != null) {
                return userKey;
            }
        }

        return ipKeyResolver.resolve(request);
    }
}
