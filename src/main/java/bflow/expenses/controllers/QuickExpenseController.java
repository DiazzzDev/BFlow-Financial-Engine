package bflow.expenses.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.expenses.DTO.QuickExpenseRequest;
import bflow.expenses.services.QuickExpenseService;
import bflow.expenses.DTO.ExpenseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Quick Expenses", description = "Fast expense logging with minimal data")
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public final class QuickExpenseController {

    /**
     * Service for quick expense operations.
     */
    private final QuickExpenseService quickExpenseService;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /**
     * Create a quick expense.
     *
     * @param request the quick expense request
     * @param authentication the authentication object
     * @return the created expense response
     */
    @Operation(
            summary = "Create an amount-only quick expense.",
            description = "Accepts only a positive amount. The server uses the "
                    + "user's default owned wallet, assigns today's date, no "
                    + "category, and the title 'Quick expense'."
    )
    @PostMapping("/quick")
    public ExpenseResponse createQuickExpense(
            @Valid @RequestBody final QuickExpenseRequest request,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        return quickExpenseService.createQuickExpense(userId, request);
    }
}
