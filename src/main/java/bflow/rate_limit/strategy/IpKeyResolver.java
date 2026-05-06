package bflow.rate_limit.strategy;

import bflow.rate_limit.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class IpKeyResolver implements KeyResolver {

    /**
     * Resolves a rate limiting key based on client IP address.
     * @param request the HTTP request to extract the IP from.
     * @return a unique key for rate limiting based on IP.
     */
    @Override
    public String resolve(final HttpServletRequest request) {
        return "ip:" + ClientIpUtil.extract(request);
    }
}
