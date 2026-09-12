package bflow.budget;

import bflow.auth.services.CurrentUserService;
import bflow.budget.DTO.BudgetDetailResponse;
import bflow.budget.DTO.BudgetPatchRequest;
import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.DTO.BudgetSummaryResponse;

import bflow.budget.DTO.BudgetSearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import bflow.budget.services.BudgetService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for managing budgets.
 */
@Tag(name = "Budgets", description = "Creation, retrieval, update, and deletion of budgets")
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public final class ControllerBudget {
    /**
     * The budget service.
     */
    private final BudgetService budgetService;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Get budgets for the authenticated user, with optional dynamic
     * filtering (case-insensitive by wallet/category name, wallet,
     * category, scope, period, or status), pagination, and sorting.
     *
     * <p>Example: {@code GET /api/v1/budgets?name=food&page=0&size=10
     * &sort=amount,desc}
     *
     * @param authentication the authentication object
     * @param filter the search criteria bound from query parameters
     * @param pageable the pagination and sorting information
     * @return response containing a page of budgets
     */
    @Operation(
            summary = "Get budgets for the authenticated user, with optional dynamic filtering (case-insensitive by wallet/category name, wallet, category, scope, period, or status), pagination, and sorting.",
            description = "Get budgets for the authenticated user, with optional dynamic filtering (case-insensitive by wallet/category name, wallet, category, scope, period, or status), pagination, and sorting. Example: GET /api/v1/budgets?name=food&page=0&size=10 &sort=amount,desc"
    )
    @GetMapping
    public ApiResponse<Page<BudgetResponse>> getBudgets(
            final Authentication authentication,
            final BudgetSearchRequest filter,
            final Pageable pageable
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        Page<BudgetResponse> budgets =
                budgetService.getBudgets(userId, filter, pageable);

        return ApiResponse.success(
                messageService.get("budget.list.retrieved"),
                budgets,
                "/api/v1/budgets"
        );
    }

    /**
     * Get the full detail view (overview, trend, recent activity) for a
     * specific budget, used by the budget dashboard UI.
     *
     * @param id the budget ID
     * @param authentication the authentication object
     * @return response containing the budget detail
     */
    @Operation(
            summary = "Get the full detail view (overview, trend, recent activity) for a specific budget, used by the budget dashboard UI.",
            description = "Get the full detail view (overview, trend, recent activity) for a specific budget, used by the budget dashboard UI."
    )
    @GetMapping("/{id}/detail")
    public ApiResponse<BudgetDetailResponse> getBudgetDetail(
            @PathVariable final UUID id,
            final Authentication authentication
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        BudgetDetailResponse response =
                budgetService.getBudgetDetail(id, userId);

        return ApiResponse.success(
                messageService.get("budget.detail.retrieved"),
                response,
                "/api/v1/budgets/" + id + "/detail"
        );
    }

    /**
     * Get all budgets for a specific wallet.
     *
     * @param walletId the wallet ID
     * @param authentication the authentication object
     * @return response containing list of budgets
     */
    @Operation(
            summary = "Get all budgets for a specific wallet.",
            description = "Get all budgets for a specific wallet."
    )
    @GetMapping("/wallet/{walletId}")
    public ApiResponse<List<BudgetResponse>> getBudgetsByWallet(
            @PathVariable final UUID walletId,
            final Authentication authentication
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        List<BudgetResponse> budgets =
                budgetService.getBudgetsByWallet(walletId, userId);

        return ApiResponse.success(
                messageService.get("budget.list.retrieved"),
                budgets,
                "/api/v1/budgets/wallet/" + walletId
        );
    }

    /**
     * Get the status of a specific budget.
     *
     * @param id the budget ID
     * @param authentication the authentication object
     * @return response containing budget status
     */
    @Operation(
            summary = "Get the status of a specific budget.",
            description = "Get the status of a specific budget."
    )
    @GetMapping("/{id}/status")
    public ApiResponse<BudgetResponse> getBudgetStatus(
            @PathVariable final UUID id,
            final Authentication authentication
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        BudgetResponse response = budgetService.getBudgetStatus(id, userId);

        return ApiResponse.success(
                messageService.get("budget.status.retrieved"),
                response,
                "/api/v1/budgets/" + id + "/status"
        );
    }

    /**
     * Create a new budget.
     *
     * @param request the budget request
     * @param authentication the authentication object
     * @return response containing created budget
     */
    @Operation(
            summary = "Create a new budget.",
            description = "Create a new budget."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BudgetResponse> createBudget(
            @RequestBody @Valid final BudgetRequest request,
            final Authentication authentication
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        BudgetResponse response =
                budgetService.createBudget(
                        request, userId, request.getWalletId());

        return ApiResponse.success(messageService.get("budget.created"), response,
                "/api/v1/budgets");
    }

    /**
     * Get budget summary for a specific wallet.
     *
     * @param walletId the wallet ID
     * @param authentication the authentication object
     * @return response containing budget summary
     */
    @Operation(
            summary = "Get budget summary for a specific wallet.",
            description = "Get budget summary for a specific wallet."
    )
    @GetMapping("/wallet/{walletId}/summary")
    public ApiResponse<BudgetSummaryResponse> getSummary(
            @PathVariable final UUID walletId,
            final Authentication authentication
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        BudgetSummaryResponse summary =
                budgetService.getBudgetSummary(walletId, userId);

        return ApiResponse.success(
                messageService.get("budget.summary.retrieved"),
                summary,
                "/api/v1/budgets/wallet/" + walletId + "/summary"
        );
    }

    /**
     * Partially update an existing budget.
     *
     * @param id the budget ID
     * @param request the patch request with updated fields
     * @param authentication the authentication object
     * @return response containing the updated budget
     */
    @Operation(
            summary = "Partially update an existing budget.",
            description = "Partially update an existing budget."
    )
    @PatchMapping("/{id}")
    public ApiResponse<BudgetResponse> patchBudget(
            @PathVariable final UUID id,
            @RequestBody @Valid final BudgetPatchRequest request,
            final Authentication authentication
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        BudgetResponse response =
                budgetService.patchBudget(id, userId, request);

        return ApiResponse.success(
                messageService.get("budget.updated"),
                response,
                "/api/v1/budgets/" + id
        );
    }

    /**
     * Delete a budget.
     *
     * @param id the budget ID
     * @param authentication the authentication object
     * @return response indicating successful deletion
     */
    @Operation(
            summary = "Delete a budget.",
            description = "Delete a budget."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBudget(
            @PathVariable final UUID id,
            final Authentication authentication
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        budgetService.deleteBudget(id, userId);

        return ApiResponse.success(
                messageService.get("budget.deleted"),
                null,
                "/api/v1/budgets/" + id
        );
    }
}
