package bflow.tranfers;

import bflow.common.response.ApiResponse;
import bflow.tranfers.DTO.TransferenceRequest;
import bflow.tranfers.DTO.TransferenceResponse;
import bflow.wallet.DTO.WalletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Controller for managing transfer operations between wallets.
 */
@RestController
@RequestMapping("/api/v1/tranfers")
@RequiredArgsConstructor
public class ControllerTransfer {
    /** The service handling transfer business logic. */
    private final ServiceTransfers serviceTransfers;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferenceResponse>> getTransferById(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        // Extract user UUID from JWT token (principal)
        String userIdString = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(userIdString);

        // Retrieve wallet with access validation
        TransferenceResponse transfer = serviceTransfers
                .getTransferById(id, userId);

        // Return success response
        ApiResponse<TransferenceResponse> response = ApiResponse.success(
                "Transfer retrieved successfully",
                transfer,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransferenceResponse>>> getUserTransfers(
            final Authentication authentication,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        // Retrieve wallet with access validation
        Page<TransferenceResponse> transfers = serviceTransfers
                .getUserTransfers(userId, pageable);

        // Return success response
        ApiResponse<Page<TransferenceResponse>> response = ApiResponse.success(
                "Transfers retrieved successfully",
                transfers,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<ApiResponse<Page<TransferenceResponse>>>
        getUserTransfersByWalletId(
            @PathVariable final UUID walletId,
            final Authentication authentication,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        // Retrieve wallet with access validation
        Page<TransferenceResponse> transfers = serviceTransfers
                .getUserTransfersByWalletId(userId, walletId, pageable);

        // Return success response
        ApiResponse<Page<TransferenceResponse>> response = ApiResponse.success(
                "Transfers retrieved successfully",
                transfers,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * Processes a transfer request between two wallets.
     * @param request the transfer request containing from/to wallet IDs
     *         and amount.
     * @param authentication the authenticated user's principal.
     * @param httpRequest the HTTP request for location header.
     * @return a ResponseEntity with the transfer response.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TransferenceResponse>> transfer(
            @Valid @RequestBody final TransferenceRequest request,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = UUID.fromString(authentication.getName());

        TransferenceResponse transferResponse =
                serviceTransfers.saveTransfer(request, userId);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transferResponse.getId())
                .toUri();

        ApiResponse<TransferenceResponse> response =
                ApiResponse.success(
                        "Transfer completed successfully",
                        transferResponse,
                        httpRequest.getRequestURI()
                );

        return ResponseEntity
                .created(location)
                .body(response);
    }
}
