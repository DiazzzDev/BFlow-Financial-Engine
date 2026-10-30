package Diaz.Dev.BFlow.dashboard.controller;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.dashboard.controller.ControllerDashboard;
import bflow.dashboard.dto.ActivityTypeFilter;
import bflow.dashboard.enums.StatisticsPeriod;
import bflow.dashboard.service.ServiceDashboard;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerDashboardTest {

    private ServiceDashboard serviceDashboard;
    private CurrentUserService currentUserService;
    private MessageService messageService;
    private ControllerDashboard controller;
    private Authentication authentication;
    private HttpServletRequest request;
    private UUID userId;

    @BeforeEach
    void setUp() {
        serviceDashboard = mock(ServiceDashboard.class);
        currentUserService = mock(CurrentUserService.class);
        messageService = mock(MessageService.class);
        authentication = mock(Authentication.class);
        request = mock(HttpServletRequest.class);
        controller = new ControllerDashboard(
                serviceDashboard, currentUserService, messageService);
        userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(authentication))
                .thenReturn(userId);
        when(request.getRequestURI()).thenReturn("/api/v1/dashboard/widget");
    }

    @Test
    void forwardsAllDashboardWidgetFiltersAndReturnsSuccessfulResponses() {
        UUID walletId = UUID.randomUUID();
        ApiResponse<?> balance = controller.getBalance(
                walletId, authentication, request);
        ApiResponse<?> statistics = controller.getStatistics(
                StatisticsPeriod.CUSTOM, 2026, 3, 10,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10),
                authentication, request);
        ApiResponse<?> averages = controller.getAverages(
                authentication, request);
        ApiResponse<?> activity = controller.getRecentActivity(
                ActivityTypeFilter.EXPENSE, "coffee", 12,
                authentication, request);
        ApiResponse<?> spending = controller.getSpending(
                authentication, request);
        ApiResponse<?> budgets = controller.getBudgetsHealth(
                authentication, request);
        ApiResponse<?> breakdown = controller.getActivityBreakdown(
                authentication, request);

        assertTrue(balance.isSuccess());
        assertTrue(statistics.isSuccess());
        assertTrue(averages.isSuccess());
        assertTrue(activity.isSuccess());
        assertTrue(spending.isSuccess());
        assertTrue(budgets.isSuccess());
        assertTrue(breakdown.isSuccess());
        verify(serviceDashboard).getBalanceSummary(userId, walletId);
        verify(serviceDashboard).getStatistics(
                userId, StatisticsPeriod.CUSTOM, 2026, 3, 10,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        verify(serviceDashboard).getAverages(userId);
        verify(serviceDashboard).getRecentActivity(
                userId, ActivityTypeFilter.EXPENSE, "coffee", 12);
        verify(serviceDashboard).getSpendingSummary(userId);
        verify(serviceDashboard).getBudgetsHealth(userId);
        verify(serviceDashboard).getActivityBreakdown(userId);
    }
}
