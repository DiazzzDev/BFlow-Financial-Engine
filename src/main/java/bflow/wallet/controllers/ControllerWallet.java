package bflow.wallet.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.income.DTO.IncomeResponse;
import bflow.wallet.DTO.UpdateWalletRequest;
import bflow.wallet.DTO.WalletInfoResponse;
import bflow.wallet.DTO.WalletMemberResponse;
import bflow.wallet.DTO.WalletRequest;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.enums.WalletScope;
import bflow.wallet.service.ServiceWallet;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for managing wallet-related operations.
 * Provides REST endpoints for wallet CRUD operations and access control.
 */
@Tag(name = "Wallets", description = "Management of the user's wallets")
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public final class ControllerWallet {

    /** The service handling wallet business logic. */
    private final ServiceWallet serviceWallet;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Retrieves the wallets accessible to the authenticated user.
     *
     * @param authentication authenticated user.
     * @param query optional search text.
     * @param role optional wallet role filter.
     * @param scope optional wallet scope filter.
     * @param pageable pagination information.
     * @param request current HTTP request.
     * @return paginated wallet list.
     */
    @Operation(
            summary = "Retrieves the wallets accessible to the authenticated user.",
            description = "Retrieves the wallets accessible to the authenticated user."
    )
    @GetMapping
    public ApiResponse<Page<WalletResponse>> getUserWallets(
            final Authentication authentication,
            @RequestParam(required = false) final String query,
            @RequestParam(required = false) final WalletRole role,
            @RequestParam(required = false) final WalletScope scope,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        Page<WalletResponse> wallets = serviceWallet
                .getUserWallets(userId, query, role, scope, pageable);

        return ApiResponse.success(
                messageService.get("wallet.list.retrieved"),
                wallets,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves the wallet "Information" panel: last activity, highest expense,
     * transaction count, initial value, and upcoming recurring transactions.
     * @param id the wallet UUID.
     * @param authentication the authenticated user's principal.
     * @param request the HTTP request for path information.
     * @return a standard API response containing the wallet info.
     */
    @Operation(
            summary = "Retrieves the wallet 'Information' panel: last activity, highest expense, transaction count, initial value, and upcoming recurring transactions.",
            description = "Retrieves the wallet 'Information' panel: last activity, highest expense, transaction count, initial value, and upcoming recurring transactions."
    )
    @GetMapping("/{id}/info")
    public ApiResponse<WalletInfoResponse> getWalletInfo(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        WalletInfoResponse info = serviceWallet.getWalletInfo(id, userId);

        return ApiResponse.success(
                messageService.get("wallet.info.retrieved"),
                info,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves a wallet by its UUID.
     * Validates that the authenticated user has access to the wallet.
     * @param id the UUID of the wallet to retrieve.
     * @param authentication the authenticated user's principal containing UUID.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing ApiResponse with WalletResponse data.
     * @throws org.springframework.security.access.AccessDeniedException
     *         if the user does not have access.
     * @throws bflow.common.exception.NotFoundException
     *         if the wallet does not exist.
     */
    @Operation(
            summary = "Retrieves a wallet by its UUID.",
            description = "Retrieves a wallet by its UUID. Validates that the authenticated user has access to the wallet."
    )
    @GetMapping("/{id}")
    public ApiResponse<WalletResponse> getWalletById(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Retrieve wallet with access validation
        WalletResponse walletResponse = serviceWallet
                .getWalletById(id, userId);

        // Return success response
        ApiResponse<WalletResponse> response = ApiResponse.success(
                messageService.get("wallet.retrieved"),
                walletResponse,
                request.getRequestURI()
        );

        return response;
    }

    /**
     * Retrieves all expenses associated with a wallet.
     *
     * @param id the wallet identifier.
     * @param pageable pagination configuration.
     * @param authentication the authenticated user.
     * @param request the HTTP request context.
     * @return a paginated list of wallet expenses.
     */
    @Operation(
            summary = "Retrieves all expenses associated with a wallet.",
            description = "Retrieves all expenses associated with a wallet."
    )
    @GetMapping("/{id}/expenses")
    public ApiResponse<Page<ExpenseResponse>> getWalletExpenses(
            @PathVariable final UUID id,
            final Pageable pageable,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Retrieve wallet with access validation
        Page<ExpenseResponse> expenseResponse = serviceWallet
                .getWalletExpenses(id, userId, pageable);

        // Return success response
        ApiResponse<Page<ExpenseResponse>> response = ApiResponse.success(
                messageService.get("wallet.expenses.retrieved"),
                expenseResponse,
                request.getRequestURI()
        );

        return response;
    }

    /**
     * Retrieves all incomes associated with a wallet.
     *
     * @param id the wallet identifier.
     * @param pageable pagination configuration.
     * @param authentication the authenticated user.
     * @param request the HTTP request context.
     * @return a paginated list of wallet incomes.
     */
    @Operation(
            summary = "Retrieves all incomes associated with a wallet.",
            description = "Retrieves all incomes associated with a wallet."
    )
    @GetMapping("/{id}/incomes")
    public ApiResponse<Page<IncomeResponse>> getWalletIncomes(
            @PathVariable final UUID id,
            final Pageable pageable,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Retrieve wallet with access validation
        Page<IncomeResponse> incomeResponse = serviceWallet
                .getWalletIncomes(id, userId, pageable);

        // Return success response
        ApiResponse<Page<IncomeResponse>> response = ApiResponse.success(
                messageService.get("wallet.incomes.retrieved"),
                incomeResponse,
                request.getRequestURI()
        );

        return response;
    }

    /**
     * Retrieves all members associated with the specified wallet.
     *
     * The authenticated user must have access to the wallet in order
     * to retrieve its members.
     *
     * @param id the wallet UUID
     * @param authentication the authenticated user context
     * @param request the incoming HTTP request
     * @return a standard API response containing the wallet members
     */
    @Operation(
            summary = "Retrieves all members associated with the specified wallet.",
            description = "Retrieves all members associated with the specified wallet. The authenticated user must have access to the wallet in order to retrieve its members."
    )
    @GetMapping("/{id}/members")
    public ApiResponse<List<WalletMemberResponse>> getWalletMembers(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        List<WalletMemberResponse> members =
                serviceWallet.getWalletMembers(
                        id,
                        userId
                );

        return ApiResponse.success(
                messageService.get("wallet.members.retrieved"),
                members,
                request.getRequestURI()
        );
    }

    /**
     * Creates a new wallet for the authenticated user.
     * The user becomes the owner of the wallet.
     * @param request the wallet creation request.
     * @param authentication the authenticated user's principal.
     * @param httpRequest the HTTP request for location header.
     * @return a ResponseEntity with 201 CREATED status and Location header.
     */
    @Operation(
            summary = "Creates a new wallet for the authenticated user.",
            description = "Creates a new wallet for the authenticated user. The user becomes the owner of the wallet."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet(
            @Valid @RequestBody final WalletRequest request,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Create wallet with user as owner
        WalletResponse walletResponse = serviceWallet
                .createWallet(request, userId);

        // Build Location URI
        URI location = ServletUriComponentsBuilder
                .fromContextPath(httpRequest)
                .path("/api/v1/wallets/{id}")
                .buildAndExpand(walletResponse.getId())
                .toUri();

        // Return success response with 201 CREATED
        ApiResponse<WalletResponse> response = ApiResponse.success(
                messageService.get("wallet.created"),
                walletResponse,
                httpRequest.getRequestURI()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    /**
     * Updates an existing wallet for the authenticated user.
     * @param id the wallet UUID to update.
     * @param request the wallet update request.
     * @param authentication the authenticated user's principal.
     * @param httpRequest the HTTP request for path information.
     * @return a ResponseEntity containing the updated wallet response.
     */
    @Operation(
            summary = "Updates an existing wallet for the authenticated user.",
            description = "Updates an existing wallet for the authenticated user."
    )
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<WalletResponse>> patchWallet(
            @PathVariable final UUID id,
            @Valid @RequestBody final UpdateWalletRequest request,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Create wallet with user as owner
        WalletResponse walletResponse = serviceWallet
                .patchWallet(id, request, userId);

        // Build Location URI
        URI location = ServletUriComponentsBuilder
                .fromContextPath(httpRequest)
                .path("/api/v1/wallets/{id}")
                .buildAndExpand(walletResponse.getId())
                .toUri();

        // Return success response with 201 CREATED
        ApiResponse<WalletResponse> response = ApiResponse.success(
                messageService.get("wallet.updated"),
                walletResponse,
                httpRequest.getRequestURI()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    /**
     * Permanently deletes a wallet the authenticated user owns.
     *
     * @param id the wallet's identifier.
     * @param authentication the current user's authentication object.
     * @return an empty 204 response confirming the deletion.
     */
    @Operation(
            summary = "Permanently deletes a wallet the authenticated user owns.",
            description = "Permanently deletes a wallet the authenticated user owns."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWallet(
            @PathVariable final UUID id,
            final Authentication authentication
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        serviceWallet.deleteWallet(id, userId);

        return ResponseEntity.noContent().build();
    }
}
