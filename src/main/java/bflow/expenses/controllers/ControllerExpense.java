package bflow.expenses.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.services.ServiceExpense;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Expenses", description = "Management of expenses recorded by the user")
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public final class ControllerExpense {
    /**
     * Service for expense business logic operations.
     */
    private final ServiceExpense serviceExpense;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Creates a new expense entry for the authenticated user's wallet.
     *
     * @param request the expense request containing expense details
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     * @return a ResponseEntity containing the created expense response with
     *         HTTP 201 Created status
     */
    @Operation(
            summary = "Creates a new expense entry for the authenticated user's wallet.",
            description = "Creates a new expense entry for the authenticated user's wallet."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> createExpense(
            @Valid @RequestBody final ExpenseRequest request,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        ExpenseResponse response = serviceExpense.newExpense(request, userId);

        return ApiResponse.success(
                        messageService.get("expense.created"),
                        response,
                        "/api/v1/expenses"
        );
    }

    /**
     * Updates an existing expense entry for the authenticated user.
     *
     * @param id the unique identifier of the expense to update
     * @param request the expense request containing updated expense details
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     * @return a ResponseEntity containing the updated expense response with
     *         HTTP 200 OK status
     */
    @Operation(
            summary = "Updates an existing expense entry for the authenticated user.",
            description = "Updates an existing expense entry for the authenticated user."
    )
    @PutMapping("/{id}")
    public ApiResponse<ExpenseResponse> updateExpense(
            @PathVariable final String id,
            @Valid @RequestBody final ExpenseRequest request,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        UUID expenseId = UUID.fromString(id);

        ExpenseResponse response = serviceExpense.updateExpense(
                expenseId,
                request,
                userId
        );

        return ApiResponse.success(
                        messageService.get("expense.updated"),
                        response,
                        "/api/v1/expenses"
        );
    }

    /**
     * Deletes an existing expense entry for the authenticated user.
     *
     * @param id the unique identifier of the expense to delete
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     */
    @Operation(
            summary = "Deletes an existing expense entry for the authenticated user.",
            description = "Deletes an existing expense entry for the authenticated user."
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @PathVariable final String id,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        UUID expenseId = UUID.fromString(id);

        serviceExpense.deleteExpense(expenseId, userId);
    }
}
