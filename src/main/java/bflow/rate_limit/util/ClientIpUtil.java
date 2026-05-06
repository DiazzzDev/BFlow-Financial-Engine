package bflow.rate_limit.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility class for extracting client IP addresses from requests.
 */
public final class ClientIpUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private ClientIpUtil() {
    }

    /**
     * Extracts the client IP address from an HTTP request.
     * @param request the HTTP request to extract the IP from.
     * @return the client IP address.
     */
    public static String extract(final HttpServletRequest request) {

        String forwarded =
                request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }

        return request.getRemoteAddr();
    }
}
