package Diaz.Dev.BFlow.dashboard.controller;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.dashboard.controller.ControllerDashboard;
import bflow.dashboard.dto.ActivityTypeFilter;
import bflow.dashboard.service.ServiceDashboard;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

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
    void forwardsActiveDashboardWidgetFiltersAndReturnsSuccessfulResponses() {
        UUID walletId = UUID.randomUUID();
        ApiResponse<?> balance = controller.getBalance(
                walletId, authentication, request);
        ApiResponse<?> averages = controller.getAverages(
                authentication, request);
        ApiResponse<?> activity = controller.getRecentActivity(
                ActivityTypeFilter.EXPENSE, "coffee", 12,
                authentication, request);
        ApiResponse<?> budgets = controller.getBudgetsHealth(
                authentication, request);

        assertTrue(balance.isSuccess());
        assertTrue(averages.isSuccess());
        assertTrue(activity.isSuccess());
        assertTrue(budgets.isSuccess());
        verify(serviceDashboard).getBalanceSummary(userId, walletId);
        verify(serviceDashboard).getAverages(userId);
        verify(serviceDashboard).getRecentActivity(
                userId, ActivityTypeFilter.EXPENSE, "coffee", 12);
        verify(serviceDashboard).getBudgetsHealth(userId);
    }
}
