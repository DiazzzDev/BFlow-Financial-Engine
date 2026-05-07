package bflow.rate_limit.util;

import bflow.rate_limit.dto.RateLimitErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Utility class for handling rate limit error responses.
 */
public final class RateLimitResponseUtil {

    /** HTTP status code for rate limit exceeded (429). */
    private static final int RATE_LIMIT_STATUS = 429;
    /** JSON object mapper for serialization. */
    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    /**
     * Private constructor to prevent instantiation.
     */
    private RateLimitResponseUtil() {
    }

    /**
     * Writes a rate limit exceeded error response.
     * @param response the HTTP response to write to.
     * @throws IOException if writing to response fails.
     */
    public static void writeExceededResponse(
            final HttpServletResponse response
    ) throws IOException {

        response.setStatus(RATE_LIMIT_STATUS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        MAPPER.writeValue(
                response.getWriter(),
                new RateLimitErrorResponse(
                        "Rate limit exceeded",
                        RATE_LIMIT_STATUS
                )
        );
    }
}
