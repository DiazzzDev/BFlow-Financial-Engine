package bflow.income;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.income.DTO.IncomeRequest;
import bflow.income.DTO.IncomeResponse;
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

/**
 * REST controller for managing income operations.
 * Provides endpoints for creating and retrieving income entries.
 * This class is designed for extension through inheritance.
 */
@Tag(name = "Income", description = "Management of income recorded by the user")
@RestController
@RequestMapping("/api/v1/incomes")
@RequiredArgsConstructor
public class ControllerIncome {
    /**
     * Service for income business logic operations.
     */
    private final ServiceIncome serviceIncome;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Creates a new income entry for the authenticated user's wallet.
     *
     * @param request the income request containing income details
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     * @return a ResponseEntity containing the created income response with
     *         HTTP 201 Created status
     */
    @Operation(
            summary = "Creates a new income entry for the authenticated user's wallet.",
            description = "Creates a new income entry for the authenticated user's wallet."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IncomeResponse> createIncome(
            @Valid @RequestBody final IncomeRequest request,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        IncomeResponse response = serviceIncome.newIncome(request, userId);

        return ApiResponse.success(
                        messageService.get("income.created"),
                        response,
                        "/api/v1/incomes"
        );
    }

    /**
     * Updates an existing income entry for the authenticated user.
     *
     * @param id the unique identifier of the income to update
     * @param request the income request containing updated income details
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     * @return a ResponseEntity containing the updated income response with
     *         HTTP 200 OK status
     */
    @Operation(
            summary = "Updates an existing income entry for the authenticated user.",
            description = "Updates an existing income entry for the authenticated user."
    )
    @PutMapping("/{id}")
    public ApiResponse<IncomeResponse> updateIncome(
            @PathVariable final String id,
            @Valid @RequestBody final IncomeRequest request,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        UUID incomeId = UUID.fromString(id);

        IncomeResponse response = serviceIncome.updateIncome(
                incomeId,
                request,
                userId
        );

        return ApiResponse.success(
                        messageService.get("income.updated"),
                        response,
                        "/api/v1/incomes"
        );
    }

    /**
     * Deletes an existing income entry for the authenticated user.
     *
     * @param id the unique identifier of the income to delete
     * @param authentication the authentication object containing the
     *        authenticated user's principal (UUID)
     */
    @Operation(
            summary = "Deletes an existing income entry for the authenticated user.",
            description = "Deletes an existing income entry for the authenticated user."
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIncome(
            @PathVariable final String id,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        UUID incomeId = UUID.fromString(id);

        serviceIncome.deleteIncome(incomeId, userId);
    }
}
