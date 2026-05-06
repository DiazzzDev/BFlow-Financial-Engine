package bflow.rate_limit.dto;

public record RateLimitErrorResponse(
        String message,
        int status
) {
}
