package bflow.wallet;

import bflow.wallet.DTO.WalletRequest;
import bflow.wallet.DTO.WalletResponse;
import bflow.common.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import lombok.AllArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import java.net.URI;
import java.util.UUID;

/**
 * Controller for managing wallet-related operations.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@AllArgsConstructor
public class ControllerWallet {

    /** The service handling wallet business logic. */
    private final ServiceWallet objServiceW;

    /**
     * Retrieves all wallets for the authenticated user.
     * @param authentication the authenticated user's principal.
     * @param pageable the pagination information.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing paginated wallet data.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<WalletResponse>>> getUserWallets(
            final Authentication authentication,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        // Extract user UUID from JWT token (principal)
        //String userIdString = (String) authentication.getPrincipal();
        //UUID userId = UUID.fromString(userIdString);

        UUID userId = UUID.fromString(authentication.getName());

        // Retrieve wallet with access validation
        Page<WalletResponse> wallets = objServiceW
                .getUserWallets(userId, pageable);

        // Return success response
        ApiResponse<Page<WalletResponse>> response = ApiResponse.success(
                "Wallets retrieved successfully",
                wallets,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
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
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletById(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        // Extract user UUID from JWT token (principal)
        String userIdString = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(userIdString);

        // Retrieve wallet with access validation
        WalletResponse walletResponse = objServiceW
                .getWalletById(id, userId);

        // Return success response
        ApiResponse<WalletResponse> response = ApiResponse.success(
                "Wallet retrieved successfully",
                walletResponse,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * Creates a new wallet for the authenticated user.
     * The user becomes the owner of the wallet.
     * @param request the wallet creation request.
     * @param authentication the authenticated user's principal.
     * @param httpRequest the HTTP request for location header.
     * @return a ResponseEntity with 201 CREATED status and Location header.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet(
            @Valid @RequestBody final WalletRequest request,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {
        // Extract user UUID from JWT token (principal)
        String userIdString = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(userIdString);

        // Create wallet with user as owner
        WalletResponse walletResponse = objServiceW
                .createWallet(request, userId);

        // Build Location URI
        URI location = ServletUriComponentsBuilder
                .fromContextPath(httpRequest)
                .path("/api/v1/wallets/{id}")
                .buildAndExpand(walletResponse.getId())
                .toUri();

        // Return success response with 201 CREATED
        ApiResponse<WalletResponse> response = ApiResponse.success(
                "Wallet created successfully",
                walletResponse,
                httpRequest.getRequestURI()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }
}
