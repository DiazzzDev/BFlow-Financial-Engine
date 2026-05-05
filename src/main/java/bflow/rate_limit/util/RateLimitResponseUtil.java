package bflow.rate_limit.util;

import bflow.rate_limit.dto.RateLimitErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

public class RateLimitResponseUtil {

    private static final ObjectMapper mapper =
            new ObjectMapper();

    private RateLimitResponseUtil() {
    }

    public static void writeExceededResponse(
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        mapper.writeValue(
                response.getWriter(),
                new RateLimitErrorResponse(
                        "Rate limit exceeded",
                        429
                )
        );
    }
}