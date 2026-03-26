package bflow.budget;

import bflow.budget.DTO.BudgetRequest;
import bflow.budget.DTO.BudgetResponse;
import bflow.budget.services.BudgetService;
import bflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class ControllerBudget {
    private final BudgetService budgetService;

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> getBudgetsByWallet(
            @PathVariable UUID walletId) {

        List<BudgetResponse> budgets = budgetService.getBudgetsByWallet(walletId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Budgets retrieved successfully",
                        budgets,
                        "/api/v1/budgets/wallet/" + walletId
                )
        );
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BudgetResponse>> getBudgetStatus(
            @PathVariable UUID id) {

        BudgetResponse response = budgetService.getBudgetStatus(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Budget status retrieved successfully",
                        response,
                        "/api/v1/budgets/" + id + "/status"
                )
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BudgetResponse>> createBudget(
            @RequestBody @Valid BudgetRequest request,
            final Authentication authentication) {

        String userIdString = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(userIdString);

        BudgetResponse response =
                budgetService.createBudget(request, userId, request.getWalletId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Budget created successfully",
                                response,
                                "/api/v1/budgets"
                        )
                );
    }
}
