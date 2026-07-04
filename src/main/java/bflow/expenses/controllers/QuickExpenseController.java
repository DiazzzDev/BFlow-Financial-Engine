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
    @PostMapping("/quick")
    public ExpenseResponse createQuickExpense(
            @Valid @RequestBody final QuickExpenseRequest request,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        return quickExpenseService.createQuickExpense(userId, request);
    }
}
