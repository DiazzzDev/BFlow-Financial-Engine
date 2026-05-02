package bflow.expenses.controllers;

import bflow.common.response.ApiResponse;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.services.ServiceExpense;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ControllerExpense {
    /**
     * Service for expense business logic operations.
     */
    private final ServiceExpense serviceExpense;

    /**
     * Creates a new expense entry for the authenticated user's wallet.
     *
     * @param request the expense request containing expense details
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     * @return a ResponseEntity containing the created expense response with
     *         HTTP 201 Created status
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @Valid @RequestBody final ExpenseRequest request,
            final Authentication authentication
    ) {
        String userIdString = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(userIdString);
        ExpenseResponse response = serviceExpense.newExpense(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Expense created successfully",
                        response,
                        "/api/v1/expenses"
                ));
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
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(
            @PathVariable final String id,
            @Valid @RequestBody final ExpenseRequest request,
            final Authentication authentication
    ) {
        String userIdString = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(userIdString);

        UUID expenseId = UUID.fromString(id);

        ExpenseResponse response = serviceExpense.updateExpense(
                expenseId,
                request,
                userId
        );

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(
                        "Expense updated successfully",
                        response,
                        "/api/v1/expenses"
                ));
    }

    /**
     * Deletes an existing expense entry for the authenticated user.
     *
     * @param id the unique identifier of the expense to delete
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     * @return a ResponseEntity with HTTP 204 No Content status
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteExpense(
            @PathVariable final String id,
            final Authentication authentication
    ) {
        String userIdString = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(userIdString);

        UUID expenseId = UUID.fromString(id);

        serviceExpense.deleteExpense(expenseId, userId);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
