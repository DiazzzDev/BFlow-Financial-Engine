package bflow.common.idempotency.filter;

import bflow.auth.services.CurrentUserService;
import bflow.common.idempotency.config.IdempotencyProperties;
import bflow.common.idempotency.entity.IdempotencyRecord;
import bflow.common.idempotency.service.IdempotencyService;
import bflow.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public final class IdempotencyFilter extends OncePerRequestFilter {

    /** Paths protected by idempotency (prefix match). */
    private static final List<String> PROTECTED_PATHS = List.of(
            "/api/v1/transfers", "/api/v1/expenses", "/api/v1/incomes"
    );

    /** Minimum HTTP status code considered successful. */
    private static final int HTTP_SUCCESS_MIN = 200;

    /** First HTTP status code that is not considered successful. */
    private static final int HTTP_SUCCESS_MAX = 300;

    /** Service responsible for idempotency operations. */
    private final IdempotencyService idempotencyService;

    /** Configuration properties for idempotency handling. */
    private final IdempotencyProperties properties;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** JSON serializer for writing cached responses. */
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return PROTECTED_PATHS.stream()
                .noneMatch(p -> request.getRequestURI().startsWith(p));
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain chain
    ) throws ServletException, IOException {

        String key = request.getHeader(properties.getHeaderName());
        if (key == null || key.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Idempotency-Key header is required",
                    request.getRequestURI()
                );
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        UUID userId = currentUserService.getCurrentUserId(authentication);
        String endpoint = request.getRequestURI();

        CachedBodyHttpServletRequest cachedRequest =
                new CachedBodyHttpServletRequest(request);
        String requestHash = idempotencyService.hashBody(
                cachedRequest.getCachedBody()
        );

        Optional<IdempotencyRecord> existing =
                idempotencyService.find(key, userId, endpoint);

        if (existing.isPresent()
                && existing.get().getExpiresAt().isAfter(Instant.now())) {

            IdempotencyRecord record = existing.get();

            if (!record.getRequestHash().equals(requestHash)) {
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "Idempotency-Key already used "
                                + "with a different request body",
                        request.getRequestURI());
                return;
            }

            response.setStatus(record.getStatusCode());
            response.setContentType("application/json");
            response.getWriter().write(record.getResponseBody());
            return;
        }

        ContentCachingResponseWrapper wrappedResponse =
                new ContentCachingResponseWrapper(response);

        chain.doFilter(cachedRequest, wrappedResponse);

        int status = wrappedResponse.getStatus();
        byte[] responseBody = wrappedResponse.getContentAsByteArray();

        if (status >= HTTP_SUCCESS_MIN && status < HTTP_SUCCESS_MAX) {
            idempotencyService.save(
                    key, userId, endpoint, requestHash, status, responseBody
            );
        }

        wrappedResponse.copyBodyToResponse();
    }

    /**
    * Writes a JSON error response consistent with the application's
    * ApiResponse format without relying on Spring MVC exception handling.
    *
    * @param response HTTP response to write.
    * @param status HTTP status code.
    * @param message error message.
    * @param path request URI.
    * @throws IOException if writing the response fails.
    */
    private void writeError(
            final HttpServletResponse response,
            final int status,
            final String message,
            final String path
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ApiResponse<Void> body = ApiResponse.error(message, path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
