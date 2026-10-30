package Diaz.Dev.BFlow.budget;

import bflow.auth.services.CurrentUserService;
import bflow.budget.ControllerBudget;
import bflow.budget.DTO.BudgetDetailResponse;
import bflow.budget.DTO.BudgetPatchRequest;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.DTO.BudgetSearchRequest;
import bflow.budget.DTO.BudgetSummaryResponse;
import bflow.budget.services.BudgetService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerBudgetTest {

    private BudgetService budgetService;
    private CurrentUserService currentUserService;
    private MessageService messageService;
    private ControllerBudget controller;
    private Authentication authentication;
    private UUID userId;

    @BeforeEach
    void setUp() {
        budgetService = mock(BudgetService.class);
        currentUserService = mock(CurrentUserService.class);
        messageService = mock(MessageService.class);
        authentication = mock(Authentication.class);
        controller = new ControllerBudget(
                budgetService, currentUserService, messageService);
        userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(authentication))
                .thenReturn(userId);
    }

    @Test
    void forwardsBudgetReadCreateUpdateAndDeleteRequests() {
        UUID budgetId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        BudgetSearchRequest filter = mock(BudgetSearchRequest.class);
        BudgetRequest createRequest = new BudgetRequest();
        createRequest.setWalletId(walletId);
        BudgetPatchRequest patchRequest = new BudgetPatchRequest();
        PageRequest pageable = PageRequest.of(0, 10);
        when(budgetService.getBudgets(userId, filter, pageable))
                .thenReturn(Page.empty(pageable));
        when(budgetService.getBudgetsByWallet(walletId, userId))
                .thenReturn(List.of());

        ApiResponse<?> list = controller.getBudgets(
                authentication, filter, pageable);
        ApiResponse<BudgetDetailResponse> detail =
                controller.getBudgetDetail(budgetId, authentication);
        ApiResponse<List<BudgetResponse>> walletBudgets =
                controller.getBudgetsByWallet(walletId, authentication);
        ApiResponse<BudgetResponse> status =
                controller.getBudgetStatus(budgetId, authentication);
        ApiResponse<BudgetResponse> created =
                controller.createBudget(createRequest, authentication);
        ApiResponse<BudgetSummaryResponse> summary =
                controller.getSummary(walletId, authentication);
        ApiResponse<BudgetResponse> patched =
                controller.patchBudget(budgetId, patchRequest, authentication);
        ApiResponse<Void> deleted =
                controller.deleteBudget(budgetId, authentication);

        assertTrue(list.isSuccess());
        assertTrue(detail.isSuccess());
        assertTrue(walletBudgets.isSuccess());
        assertTrue(status.isSuccess());
        assertTrue(created.isSuccess());
        assertTrue(summary.isSuccess());
        assertTrue(patched.isSuccess());
        assertTrue(deleted.isSuccess());
        verify(budgetService).getBudgets(userId, filter, pageable);
        verify(budgetService).getBudgetDetail(budgetId, userId);
        verify(budgetService).getBudgetsByWallet(walletId, userId);
        verify(budgetService).getBudgetStatus(budgetId, userId);
        verify(budgetService).createBudget(createRequest, userId, walletId);
        verify(budgetService).getBudgetSummary(walletId, userId);
        verify(budgetService).patchBudget(budgetId, userId, patchRequest);
        verify(budgetService).deleteBudget(budgetId, userId);
    }
}
