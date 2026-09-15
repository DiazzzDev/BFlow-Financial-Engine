package bflow.common.audit.filter;

import bflow.auth.services.CurrentUserService;
import bflow.common.audit.service.ServiceAudit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Writes one {@link bflow.common.audit.entity.AuditRecord} per
 * state-changing request, regardless of which client made it — the web
 * app, bflow-mcp, or anything else (ADR-0010 §9).
 *
 * <p>{@code X-BFlow-Actor-Type}/{@code X-BFlow-Channel} (sent by
 * bflow-mcp's {@code BflowApiClient}) are read here purely as labels
 * for this record — they are never consulted for authorization. If
 * absent (the normal case for a web request), this defaults to
 * {@code actorType=USER, source=WEB}.</p>
 */
@RequiredArgsConstructor
public final class AuditFilter extends OncePerRequestFilter {

    /** Paths audited (prefix match) — every write surface bflow-mcp or
     *  the web client can reach, plus transfers, the highest-risk one. */
    private static final List<String> AUDITED_PATHS = List.of(
            "/api/v1/expenses", "/api/v1/incomes", "/api/v1/budgets",
            "/api/v1/recurring", "/api/v1/transfers", "/api/v1/wallets"
    );

    /** HTTP methods considered state-changing. */
    private static final Set<String> STATE_CHANGING_METHODS =
            Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Header bflow-mcp sends identifying the request as agent-originated. */
    private static final String HEADER_ACTOR_TYPE = "X-BFlow-Actor-Type";

    /** Header bflow-mcp sends identifying the request's interface. */
    private static final String HEADER_CHANNEL = "X-BFlow-Channel";

    /** Header carrying a per-request correlation id. */
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    /** Service that persists the record. */
    private final ServiceAudit serviceAudit;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        if (!STATE_CHANGING_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        return AUDITED_PATHS.stream()
                .noneMatch(p -> request.getRequestURI().startsWith(p));
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {

        chain.doFilter(request, response);

        // Recorded AFTER the chain completes, so response.getStatus()
        // reflects what actually happened — including anything
        // GlobalExceptionHandler translated a thrown exception into.
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            // Nothing authenticated (e.g. a request rejected before
            // reaching this point) — nothing meaningful to attribute
            // the record to.
            return;
        }

        UUID userId = currentUserService.getCurrentUserId(authentication);

        boolean isAgentOriginated =
                "mcp".equalsIgnoreCase(request.getHeader(HEADER_CHANNEL));

        String actorType = isAgentOriginated
                ? valueOrDefault(request.getHeader(HEADER_ACTOR_TYPE), "AGENT")
                : "USER";
        String source = isAgentOriginated ? "MCP" : "WEB";

        serviceAudit.record(
                userId,
                actorType,
                source,
                request.getMethod(),
                request.getRequestURI(),
                resourceTypeOf(request.getRequestURI()),
                response.getStatus(),
                request.getHeader(HEADER_CORRELATION_ID)
        );
    }

    private String valueOrDefault(final String value, final String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String resourceTypeOf(final String path) {
        for (String prefix : AUDITED_PATHS) {
            if (path.startsWith(prefix)) {
                // "/api/v1/expenses" -> "EXPENSES"
                String segment = prefix.substring(prefix.lastIndexOf('/') + 1);
                return segment.toUpperCase();
            }
        }
        return "UNKNOWN";
    }
}