package bflow.common.financial;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller that exposes transaction history endpoints.
 */
@Tag(name = "Financial History", description = "Query the combined history of financial movements")
@RestController
@RequiredArgsConstructor
public class ControllerTransactionHistory {

    /**
     * Service responsible for retrieving transaction history.
     */
    private final ServiceTransactionHistory serviceTransactionHistory;

    /**
     * Service that resolves the authenticated user.
     */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Retrieves the unified transaction history for the authenticated user.
     *
     * @param authentication current authenticated user.
     * @param query optional search term used to filter transactions.
     * @param type optional transaction type filter.
     * @param contributorIds optional list of contributor user ids to
     *         restrict results to (e.g. one or more wallet members).
     * @param pageable pagination information.
     * @param request current HTTP request.
     * @return paginated transaction history.
     */
    @Operation(
            summary = "Retrieves the unified transaction history for the authenticated user.",
            description = "Retrieves the unified transaction history for the authenticated user."
    )
    @GetMapping("/api/v1/transactions")
    public ApiResponse<Page<TransactionResponse>> getGlobalHistory(
            final Authentication authentication,
            @RequestParam(required = false) final String query,
            @RequestParam(required = false) final TransactionType type,
            @RequestParam(required = false) final List<UUID> contributorIds,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        Page<TransactionResponse> history = serviceTransactionHistory
                .getGlobalHistory(
                        userId, query, type, contributorIds, pageable
                );

        return ApiResponse.success(
                messageService.get("transactionHistory.retrieved"),
                history,
                request.getRequestURI()
        );
    }

    /**
     * Retrieves the transaction history for a specific wallet.
     *
     * @param walletId wallet identifier.
     * @param authentication current authenticated user.
     * @param query optional search term used to filter transactions.
     * @param type optional transaction type filter.
     * @param contributorIds optional list of contributor user ids to
     *         restrict results to (e.g. one or more wallet members).
     * @param pageable pagination information.
     * @param request current HTTP request.
     * @return paginated wallet transaction history.
     */
    @Operation(
            summary = "Retrieves the transaction history for a specific wallet.",
            description = "Retrieves the transaction history for a specific wallet."
    )
    @GetMapping("/api/v1/wallets/{walletId}/transactions")
    public ApiResponse<Page<TransactionResponse>> getWalletHistory(
            @PathVariable final UUID walletId,
            final Authentication authentication,
            @RequestParam(required = false) final String query,
            @RequestParam(required = false) final TransactionType type,
            @RequestParam(required = false) final List<UUID> contributorIds,
            final Pageable pageable,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        Page<TransactionResponse> history = serviceTransactionHistory
                .getWalletHistory(
                        walletId, userId, query, type,
                        contributorIds, pageable
                );

        return ApiResponse.success(
                messageService.get("transactionHistory.wallet.retrieved"),
                history,
                request.getRequestURI()
        );
    }
}
