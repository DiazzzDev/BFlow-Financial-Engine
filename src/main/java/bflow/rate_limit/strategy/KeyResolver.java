package bflow.rate_limit.strategy;

import jakarta.servlet.http.HttpServletRequest;

public interface KeyResolver {
    /**
     * Resolves a unique key for rate limiting based on the request.
     * @param request the HTTP request to resolve the key from.
     * @return a unique key for rate limiting.
     */
    String resolve(HttpServletRequest request);
}
