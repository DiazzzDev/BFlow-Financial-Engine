package bflow.dashboard.controller;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.dashboard.dto.ActivityBreakdownResponse;
import bflow.dashboard.dto.AveragesResponse;
import bflow.dashboard.dto.BalanceSummaryResponse;
import bflow.dashboard.dto.BudgetHealthItem;
import bflow.dashboard.dto.RecentActivityItem;
import bflow.dashboard.dto.SpendingSummaryResponse;
import bflow.dashboard.dto.StatisticsResponse;
import bflow.dashboard.service.ServiceDashboard;
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
            summary = "Retrieves the 'Balance total' widget data.",
            description = "Retrieves the 'Balance total' widget data."
    )
    @GetMapping("/balance")
    public ApiResponse<BalanceSummaryResponse> getBalance(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        BalanceSummaryResponse balance = serviceDashboard
                .getBalanceSummary(userId);

        return ApiResponse.success(
                messageService.get("dashboard.balance.retrieved"),
                balance,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves the "Statistics" widget data (income vs expenses, Jan-Dec).
     *
     * @param year optional target year; defaults to the current year.
     * @param authentication authenticated user.
     * @param request current HTTP request.
     * @return the monthly statistics series.
     */
    @Operation(
            summary = "Retrieves the 'Statistics' widget data (income vs expenses, Jan-Dec).",
            description = "Retrieves the 'Statistics' widget data (income vs expenses, Jan-Dec)."
    )
    @GetMapping("/statistics")
    public ApiResponse<StatisticsResponse> getStatistics(
            @RequestParam(required = false) final Integer year,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        StatisticsResponse statistics = serviceDashboard
                .getStatistics(userId, year);

        return ApiResponse.success(
                messageService.get("dashboard.statistics.retrieved"),
                statistics,
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
            summary = "Retrieves the 'Recent activity' widget data (top 5 transactions).",
            description = "Retrieves the 'Recent activity' widget data (top 5 transactions)."
    )
    @GetMapping("/recent-activity")
    public ApiResponse<List<RecentActivityItem>> getRecentActivity(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        List<RecentActivityItem> activity = serviceDashboard
                .getRecentActivity(userId);

        return ApiResponse.success(
                messageService.get("dashboard.recentActivity.retrieved"),
                activity,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves the "Spending this month" widget data.
     *
     * @param authentication authenticated user.
     * @param request current HTTP request.
     * @return the spending summary.
     */
    @Operation(
            summary = "Retrieves the 'Spending this month' widget data.",
            description = "Retrieves the 'Spending this month' widget data."
    )
    @GetMapping("/spending")
    public ApiResponse<SpendingSummaryResponse> getSpending(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        SpendingSummaryResponse spending = serviceDashboard
                .getSpendingSummary(userId);

        return ApiResponse.success(
                messageService.get("dashboard.spending.retrieved"),
                spending,
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
            description = "Retrieves the 'Budgets health' widget data (top 3 budgets)."
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

    /**
     * Retrieves the "Activity breakdown" widget data (income/expense/transfer
     * share this month, plus activity change vs last month).
     *
     * @param authentication authenticated user.
     * @param request current HTTP request.
     * @return the activity breakdown.
     */
    @Operation(
            summary = "Retrieves the 'Activity breakdown' widget data (income/expense/transfer share this month, plus activity change vs last month).",
            description = "Retrieves the 'Activity breakdown' widget data (income/expense/transfer share this month, plus activity change vs last month)."
    )
    @GetMapping("/activity-breakdown")
    public ApiResponse<ActivityBreakdownResponse> getActivityBreakdown(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        ActivityBreakdownResponse breakdown = serviceDashboard
                .getActivityBreakdown(userId);

        return ApiResponse.success(
                messageService.get("dashboard.activityBreakdown.retrieved"),
                breakdown,
                request.getRequestURI()
        );
    }
}
