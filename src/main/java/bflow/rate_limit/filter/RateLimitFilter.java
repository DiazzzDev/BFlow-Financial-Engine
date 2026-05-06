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
public class RateLimitFilter extends OncePerRequestFilter {

    private final EndpointPolicyResolver endpointPolicyResolver;
    private final RateLimitPolicyRegistry policyRegistry;
    private final RateLimitService rateLimitService;

    private final IpKeyResolver ipKeyResolver;
    private final UserKeyResolver userKeyResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
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

        System.out.println("---- RATE LIMIT DEBUG ----");
        System.out.println("Path: " + request.getRequestURI());
        System.out.println("Policy: " + policyKey);
        System.out.println("BucketKey: " + bucketKey);
        System.out.println("--------------------------");

        ConsumptionProbe probe =
                rateLimitService.tryConsume(bucketKey, policy);

        if (!probe.isConsumed()) {

            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setHeader(
                    "X-Rate-Limit-Retry-After",
                    String.valueOf(
                            probe.getNanosToWaitForRefill() / 1_000_000_000
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

    private String resolveBucketKey(HttpServletRequest request) {

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