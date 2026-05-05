package bflow.rate_limit.strategy;

import jakarta.servlet.http.HttpServletRequest;

public interface KeyResolver {
    String resolve(HttpServletRequest request);
}
