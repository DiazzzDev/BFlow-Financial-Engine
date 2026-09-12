package bflow.wallet.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.wallet.DTO.WalletInvitationRequest;
import bflow.wallet.DTO.WalletInvitationResponse;
import bflow.wallet.DTO.CollaboratorSearchResult;
import bflow.wallet.DTO.WalletInvitationSentResponse;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.service.ServiceWalletSharing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Wallet Invitations", description = "Invite and manage collaborators on a shared wallet")
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class ControllerWalletInvitation {

    /**
     * Service responsible for wallet sharing operations.
     */
    private final ServiceWalletSharing serviceWalletSharing;

    /**
     * Service used to retrieve the authenticated user.
     */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Searches for users that can be invited to a wallet, by name or
     * email. Only the wallet owner can perform this search.
     *
     * @param walletId the wallet UUID
     * @param query the search text (name or email fragment)
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return up to 8 matching users
     */
    @Operation(
            summary = "Searches for users that can be invited to a wallet, by name or email.",
            description = "Searches for users that can be invited to a wallet, by name or email. Only the wallet owner can perform this search."
    )
    @GetMapping("/{walletId}/collaborators/search")
    public ApiResponse<List<CollaboratorSearchResult>> searchCollaborators(
            @PathVariable final UUID walletId,
            @RequestParam(required = false) final String query,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        List<CollaboratorSearchResult> response =
            serviceWalletSharing.searchCollaborators(walletId, userId, query);

        return ApiResponse.success(
                messageService.get("wallet.collaborators.retrieved"),
                response,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Sends a wallet invitation to a user.
     *
     * @param walletId the wallet UUID
     * @param request the invitation request
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return the created invitation
     */
    @Operation(
            summary = "Sends a wallet invitation to a user.",
            description = "Sends a wallet invitation to a user."
    )
    @PostMapping("/{walletId}/invitations")
    public ApiResponse<WalletInvitationResponse> inviteMember(
            @PathVariable final UUID walletId,
            @Valid @RequestBody final WalletInvitationRequest request,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        WalletInvitationResponse response =
                serviceWalletSharing.inviteMember(
                        walletId,
                        userId,
                        request.getInvitedEmail()
                );

        return ApiResponse.success(
                messageService.get("wallet.invitation.sent"),
                response,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Accepts a wallet invitation.
     *
     * @param token the invitation token
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return the updated wallet information
     */
    @Operation(
            summary = "Accepts a wallet invitation.",
            description = "Accepts a wallet invitation."
    )
    @PostMapping("/invitations/{token}/accept")
    public ApiResponse<WalletResponse> acceptInvitation(
            @PathVariable final String token,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        WalletResponse response =
                serviceWalletSharing.acceptInvitation(
                        token,
                        userId
                );

        return ApiResponse.success(
                messageService.get("wallet.invitation.accepted"),
                response,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Accepts a wallet invitation by its id, for the in-app "pending
     * invitations" list, where the token (only delivered by email)
     * isn't available.
     *
     * @param invitationId the invitation UUID
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return the updated wallet information
     */
    @Operation(
            summary = "Accepts a wallet invitation by its id.",
            description = "Accepts a wallet invitation by its id, for use from the in-app "
                    + "pending-invitations list, where the email token isn't available."
    )
    @PostMapping("/invitations/id/{invitationId}/accept")
    public ApiResponse<WalletResponse> acceptInvitationById(
            @PathVariable final UUID invitationId,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        WalletResponse response =
                serviceWalletSharing.acceptInvitationById(
                        invitationId,
                        userId
                );

        return ApiResponse.success(
                messageService.get("wallet.invitation.accepted"),
                response,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Rejects a wallet invitation.
     *
     * @param token the invitation token
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return a successful response
     */
    @Operation(
            summary = "Rejects a wallet invitation.",
            description = "Rejects a wallet invitation."
    )
    @PostMapping("/invitations/{token}/reject")
    public ApiResponse<Void> rejectInvitation(
            @PathVariable final String token,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        serviceWalletSharing.rejectInvitation(
                token,
                userId
        );

        return ApiResponse.success(
                messageService.get("wallet.invitation.rejected"),
                null,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Rejects a wallet invitation by its id, for the in-app "pending
     * invitations" list, where the token (only delivered by email)
     * isn't available.
     *
     * @param invitationId the invitation UUID
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return a successful response
     */
    @Operation(
            summary = "Rejects a wallet invitation by its id.",
            description = "Rejects a wallet invitation by its id, for use from the in-app "
                    + "pending-invitations list, where the email token isn't available."
    )
    @PostMapping("/invitations/id/{invitationId}/reject")
    public ApiResponse<Void> rejectInvitationById(
            @PathVariable final UUID invitationId,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        serviceWalletSharing.rejectInvitationById(
                invitationId,
                userId
        );

        return ApiResponse.success(
                messageService.get("wallet.invitation.rejected"),
                null,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Cancels a pending wallet invitation.
     *
     * @param invitationId the invitation UUID
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return a successful response
     */
    @Operation(
            summary = "Cancels a pending wallet invitation.",
            description = "Cancels a pending wallet invitation."
    )
    @DeleteMapping("/invitations/{invitationId}")
    public ApiResponse<Void> cancelInvitation(
            @PathVariable final UUID invitationId,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        serviceWalletSharing.cancelInvitation(
                invitationId,
                userId
        );

        return ApiResponse.success(
                messageService.get("wallet.invitation.canceled"),
                null,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Removes a member from a shared wallet.
     *
     * @param walletId the wallet UUID
     * @param memberId the member UUID
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return a successful response
     */
    @Operation(
            summary = "Removes a member from a shared wallet.",
            description = "Removes a member from a shared wallet."
    )
    @DeleteMapping("/{walletId}/members/{memberId}")
    public ApiResponse<Void> removeMember(
            @PathVariable final UUID walletId,
            @PathVariable final UUID memberId,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        serviceWalletSharing.removeMember(
                walletId,
                userId,
                memberId
        );

        return ApiResponse.success(
                messageService.get("wallet.member.removed"),
                null,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Leaves a shared wallet. The authenticated user removes
     * themselves — the self-service counterpart to
     * {@link #removeMember}, which only the owner can invoke on
     * someone else. The owner cannot leave this way; they must
     * transfer ownership or delete the wallet instead.
     *
     * @param walletId the wallet UUID
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return a successful response
     */
    @Operation(
            summary = "Leaves a shared wallet.",
            description = "Removes the authenticated user from the specified wallet. "
                    + "The wallet owner cannot leave this way — ownership must be "
                    + "transferred, or the wallet deleted, instead."
    )
    @DeleteMapping("/{walletId}/members/me")
    public ApiResponse<Void> leaveWallet(
            @PathVariable final UUID walletId,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        serviceWalletSharing.leaveWallet(walletId, userId);

        return ApiResponse.success(
                messageService.get("wallet.left"),
                null,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Retrieves all pending invitations for the authenticated user.
     *
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return the list of pending invitations
     */
    @Operation(
            summary = "Retrieves all pending invitations for the authenticated user.",
            description = "Retrieves all pending invitations for the authenticated user."
    )
    @GetMapping("/invitations")
    public ApiResponse<List<WalletInvitationResponse>> getPendingInvitations(
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        List<WalletInvitationResponse> response =
                serviceWalletSharing.getPendingInvitations(userId);

        return ApiResponse.success(
                messageService.get("wallet.invitations.pending.retrieved"),
                response,
                httpRequest.getRequestURI()
        );
    }

    /**
     * Retrieves every invitation sent by the authenticated user for
     * the specified wallet — including invitations still pending a
     * response, not only accepted ones.
     *
     * @param walletId the wallet UUID to filter invitations by
     * @param authentication the authenticated user
     * @param httpRequest the current HTTP request
     * @return the list of invitations the user has sent for that wallet
     */
    @Operation(
            summary = "Retrieves every invitation sent by the authenticated user for a specific wallet — including invitations still pending a response, not only accepted ones.",
            description = "Retrieves every invitation sent by the authenticated user for a specific wallet — including invitations still pending a response, not only accepted ones."
    )
    @GetMapping("/{walletId}/invitations/sent")
    public ApiResponse<List<WalletInvitationSentResponse>> getSentInvitations(
            @PathVariable final UUID walletId,
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        List<WalletInvitationSentResponse> response =
                serviceWalletSharing.getSentInvitations(walletId, userId);

        return ApiResponse.success(
                messageService.get("wallet.invitations.sent.retrieved"),
                response,
                httpRequest.getRequestURI()
        );
    }
}
