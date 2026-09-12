package bflow.tranfers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.tranfers.DTO.TransferenceRequest;
import bflow.tranfers.DTO.TransferenceResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for managing transfer operations between wallets.
 * Provides REST endpoints for retrieving transfer information.
 */
@Tag(name = "Transfers", description = "Fund transfers between wallets")
@RestController
@RequestMapping("/api/v1/tranfers")
@RequiredArgsConstructor
public final class ControllerTransfer {
    /** The service handling transfer business logic. */
    private final ServiceTransfers serviceTransfers;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Retrieves a transfer by its unique identifier.
     * @param id the transfer UUID.
     * @param authentication the current user's authentication object.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing the transfer response.
     */
    @Operation(
            summary = "Retrieves a transfer by its unique identifier.",
            description = "Retrieves a transfer by its unique identifier."
    )
    @GetMapping("/{id}")
    public ApiResponse<TransferenceResponse> getTransferById(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Retrieve wallet with access validation
        TransferenceResponse transfer = serviceTransfers
                .getTransferById(id, userId);

        // Return success response
        ApiResponse<TransferenceResponse> response = ApiResponse.success(
                messageService.get("transfer.retrieved"),
                transfer,
                request.getRequestURI()
        );

        return response;
    }

    /**
     * Retrieves all transfers for the authenticated user.
     * @param authentication the current user's authentication object.
     * @param pageable the pagination information.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing paginated transfer responses.
     */
    @Operation(
            summary = "Retrieves all transfers for the authenticated user.",
            description = "Retrieves all transfers for the authenticated user."
    )
    @GetMapping
    public ApiResponse<Page<TransferenceResponse>> getUserTransfers(
            final Authentication authentication,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Retrieve wallet with access validation
        Page<TransferenceResponse> transfers = serviceTransfers
                .getUserTransfers(userId, pageable);

        // Return success response
        ApiResponse<Page<TransferenceResponse>> response = ApiResponse.success(
                messageService.get("transfers.retrieved"),
                transfers,
                request.getRequestURI()
        );

        return response;
    }

    /**
     * Retrieves transfers for a specific wallet by wallet ID.
     * @param walletId the wallet UUID to retrieve transfers for.
     * @param authentication the current user's authentication object.
     * @param pageable the pagination information.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing paginated transfer responses.
     */
    @Operation(
            summary = "Retrieves transfers for a specific wallet by wallet ID.",
            description = "Retrieves transfers for a specific wallet by wallet ID."
    )
    @GetMapping("/wallet/{walletId}")
    public ApiResponse<Page<TransferenceResponse>> getUserTransfersByWalletId(
            @PathVariable final UUID walletId,
            final Authentication authentication,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        // Retrieve wallet with access validation
        Page<TransferenceResponse> transfers = serviceTransfers
                .getUserTransfersByWalletId(userId, walletId, pageable);

        // Return success response
        ApiResponse<Page<TransferenceResponse>> response = ApiResponse.success(
                messageService.get("transfers.retrieved"),
                transfers,
                request.getRequestURI()
        );

        return response;
    }

    /**
     * Processes a transfer request between two wallets.
     * @param request the transfer request containing from/to wallet IDs
     *         and amount.
     * @param authentication the authenticated user's principal.
     * @param httpRequest the HTTP request for location header.
     * @return a ResponseEntity with the transfer response.
     */
    @Operation(
            summary = "Processes a transfer request between two wallets.",
            description = "Processes a transfer request between two wallets."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<TransferenceResponse>> transfer(
            @Valid @RequestBody final TransferenceRequest request,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        TransferenceResponse transferResponse =
                serviceTransfers.saveTransfer(request, userId);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transferResponse.getId())
                .toUri();

        ApiResponse<TransferenceResponse> response =
                ApiResponse.success(
                        messageService.get("transfer.completed"),
                        transferResponse,
                        httpRequest.getRequestURI()
                );

        return ResponseEntity
                .created(location)
                .body(response);
    }
}
