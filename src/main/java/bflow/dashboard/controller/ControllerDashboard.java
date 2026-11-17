package bflow.dashboard.controller;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.dashboard.dto.ActivityTypeFilter;
import bflow.dashboard.dto.AveragesResponse;
import bflow.dashboard.dto.BalanceSummaryResponse;
import bflow.dashboard.dto.BudgetHealthItem;
import bflow.dashboard.dto.RecentActivityItem;
import bflow.dashboard.service.ServiceDashboard;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller exposing per-widget endpoints for the main dashboard.
 */
@Tag(name = "Dashboard", description = "Aggregated data and summaries for the main dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public final class ControllerDashboard {

    /** Service handling dashboard aggregation logic. */
    private final ServiceDashboard serviceDashboard;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Retrieves the "Balance total" widget data.
     *
     * @param authentication authenticated user.
     * @param request current HTTP request.
     * @return the balance summary.
     */
    @Operation(
            summary = "Retrieves the 'Tienes ahora' balance widget.",
            description = "Current balance across every wallet the caller "
                    + "belongs to, or only walletId when provided. monthIncome "
                    + "and monthExpenses use the same wallet scope. "
                    + "percentageChangeLastMonth is null when walletId is given."
    )
    @GetMapping("/balance")
    public ApiResponse<BalanceSummaryResponse> getBalance(
            @Parameter(description = "Optional wallet filter; omit for all wallets.")
            @RequestParam(required = false) final UUID walletId,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        BalanceSummaryResponse balance = serviceDashboard
                .getBalanceSummary(userId, walletId);

        return ApiResponse.success(
                messageService.get("dashboard.balance.retrieved"),
                balance,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves the "Average income" / "Average expenses" widget data.
     *
     * @param authentication authenticated user.
     * @param request current HTTP request.
     * @return the average's response.
     */
    @Operation(
            summary = "Retrieves the 'Average income' / 'Average expenses' widget data.",
            description = "Retrieves the 'Average income' / 'Average expenses' widget data."
    )
    @GetMapping("/averages")
    public ApiResponse<AveragesResponse> getAverages(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        AveragesResponse averages = serviceDashboard.getAverages(userId);

        return ApiResponse.success(
                messageService.get("dashboard.averages.retrieved"),
                averages,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves the "Recent activity" widget data (top 5 transactions).
     *
     * @param authentication authenticated user.
     * @param request current HTTP request.
     * @return up to 5 recent activity items.
     */
    @Operation(
            summary = "Retrieves the 'Recent activity' widget data.",
            description = "Most recent incomes and expenses across the caller's "
                    + "wallets, newest first. type filters by direction (ALL by "
                    + "default); query matches title or description; limit is "
                    + "clamped to 1-50 (default 5). Transfers are not included."
    )
    @GetMapping("/recent-activity")
    public ApiResponse<List<RecentActivityItem>> getRecentActivity(
            @Parameter(description = "Direction filter.",
                    schema = @Schema(implementation = ActivityTypeFilter.class))
            @RequestParam(defaultValue = "ALL") final ActivityTypeFilter type,
            @Parameter(description = "Optional text matched against title and description.")
            @RequestParam(required = false) final String query,
            @Parameter(description = "Maximum items, 1-50.")
            @RequestParam(defaultValue = "5") final int limit,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        List<RecentActivityItem> activity = serviceDashboard
                .getRecentActivity(userId, type, query, limit);

        return ApiResponse.success(
                messageService.get("dashboard.recentActivity.retrieved"),
                activity,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves the "Budgets health" widget data (top 3 budgets).
     *
     * @param authentication authenticated user.
     * @param request current HTTP request.
     * @return up to 3 budget health items.
     */
    @Operation(
            summary = "Retrieves the 'Budgets health' widget data (top 3 budgets).",
            description = "Returns up to three budgets with the highest spending "
                    + "percentage. "
                    + "Each item contains budgetLimit, spent, remaining "
                    + "(budgetLimit minus spent, never below zero), and "
                    + "percentage spent. All monetary fields use the item's "
                    + "currency; an empty array means the user has no budgets."
    )
    @GetMapping("/budgets-health")
    public ApiResponse<List<BudgetHealthItem>> getBudgetsHealth(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        List<BudgetHealthItem> health = serviceDashboard
                .getBudgetsHealth(userId);

        return ApiResponse.success(
                messageService.get("dashboard.budgetsHealth.retrieved"),
                health,
                request.getRequestURI()
        );
    }

}
