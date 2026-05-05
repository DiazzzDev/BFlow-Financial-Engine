package bflow.rate_limit.util;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpUtil {

    private ClientIpUtil() {
    }

    public static String extract(HttpServletRequest request) {

        String forwarded =
                request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }

        return request.getRemoteAddr();
    }
}