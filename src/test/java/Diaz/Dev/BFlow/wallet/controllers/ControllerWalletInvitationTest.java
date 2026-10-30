package Diaz.Dev.BFlow.wallet.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.wallet.DTO.WalletInvitationRequest;
import bflow.wallet.DTO.WalletInvitationResponse;
import bflow.wallet.DTO.CollaboratorSearchResult;
import bflow.wallet.DTO.WalletInvitationSentResponse;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.controllers.ControllerWalletInvitation;
import bflow.wallet.enums.WalletInvitationStatus;
import bflow.wallet.service.ServiceWalletSharing;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControllerWalletInvitationTest {

    @Mock
    private ServiceWalletSharing serviceWalletSharing;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private MessageService messageService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    private ControllerWalletInvitation controller;
    private UUID userId;

    @BeforeEach
    void setUp() {
        controller = new ControllerWalletInvitation(
                serviceWalletSharing, currentUserService, messageService);
        userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(authentication))
                .thenReturn(userId);
    }

    @Test
    void invitingCollaboratorReturnsPendingInvitationDetails() {
        UUID walletId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        WalletInvitationRequest invitationRequest =
                new WalletInvitationRequest();
        invitationRequest.setInvitedEmail("guest@example.com");
        WalletInvitationResponse invitation = new WalletInvitationResponse(
                invitationId, walletId, "Home", "guest@example.com",
                "Owner", "owner@example.com", null,
                WalletInvitationStatus.PENDING, Instant.parse(
                        "2026-11-01T12:00:00Z"));
        when(serviceWalletSharing.inviteMember(
                walletId, userId, "guest@example.com"))
                .thenReturn(invitation);
        when(messageService.get("wallet.invitation.sent"))
                .thenReturn("Invitation sent.");
        when(request.getRequestURI())
                .thenReturn("/api/v1/wallets/" + walletId + "/invitations");

        ApiResponse<WalletInvitationResponse> response =
                controller.inviteMember(
                        walletId, invitationRequest, authentication, request);

        assertTrue(response.isSuccess());
        assertEquals("Invitation sent.", response.getMessage());
        assertEquals("/api/v1/wallets/" + walletId + "/invitations",
                response.getPath());
        assertEquals(invitationId, response.getData().getId());
        assertEquals("guest@example.com",
                response.getData().getInvitedEmail());
        assertEquals(WalletInvitationStatus.PENDING,
                response.getData().getStatus());
        verify(serviceWalletSharing).inviteMember(
                walletId, userId, "guest@example.com");
    }

    @Test
    void rejectingInvitationReturnsSuccessfulEmptyResult() {
        String token = "invitation-token";
        when(messageService.get("wallet.invitation.rejected"))
                .thenReturn("Invitation rejected.");
        when(request.getRequestURI())
                .thenReturn("/api/v1/wallets/invitations/"
                        + token + "/reject");

        ApiResponse<Void> response = controller.rejectInvitation(
                token, authentication, request);

        assertTrue(response.isSuccess());
        assertEquals("Invitation rejected.", response.getMessage());
        assertEquals("/api/v1/wallets/invitations/" + token + "/reject",
                response.getPath());
        assertNull(response.getData());
        verify(serviceWalletSharing).rejectInvitation(token, userId);
    }

    @Test
    void searchingCollaboratorsReturnsCandidates() {
        UUID walletId = UUID.randomUUID();
        List<CollaboratorSearchResult> matches = List.of();
        when(serviceWalletSharing.searchCollaborators(
                walletId, userId, "guest"))
                .thenReturn(matches);
        when(messageService.get("wallet.collaborators.retrieved"))
                .thenReturn("Collaborators retrieved.");
        when(request.getRequestURI())
                .thenReturn("/api/v1/wallets/" + walletId
                        + "/collaborators/search");

        ApiResponse<List<CollaboratorSearchResult>> response =
                controller.searchCollaborators(
                        walletId, "guest", authentication, request);

        assertTrue(response.isSuccess());
        assertEquals(matches, response.getData());
        assertEquals("Collaborators retrieved.", response.getMessage());
        verify(serviceWalletSharing).searchCollaborators(
                walletId, userId, "guest");
    }

    @Test
    void acceptingInvitationsByTokenOrIdReturnsWallet() {
        UUID invitationId = UUID.randomUUID();
        WalletResponse wallet = WalletResponse.builder()
                .id(UUID.randomUUID())
                .name("Shared")
                .build();
        when(serviceWalletSharing.acceptInvitation("token", userId))
                .thenReturn(wallet);
        when(serviceWalletSharing.acceptInvitationById(
                invitationId, userId)).thenReturn(wallet);
        when(messageService.get("wallet.invitation.accepted"))
                .thenReturn("Invitation accepted.");
        when(request.getRequestURI()).thenReturn(
                "/api/v1/wallets/invitations/accepted");

        ApiResponse<WalletResponse> tokenResponse =
                controller.acceptInvitation("token", authentication, request);
        ApiResponse<WalletResponse> idResponse =
                controller.acceptInvitationById(
                        invitationId, authentication, request);

        assertTrue(tokenResponse.isSuccess());
        assertTrue(idResponse.isSuccess());
        assertEquals(wallet, tokenResponse.getData());
        assertEquals(wallet, idResponse.getData());
        verify(serviceWalletSharing).acceptInvitation("token", userId);
        verify(serviceWalletSharing).acceptInvitationById(
                invitationId, userId);
    }

    @Test
    void rejectsByInvitationIdCancelsAndRemovesMembers() {
        UUID invitationId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(messageService.get("wallet.invitation.rejected"))
                .thenReturn("Invitation rejected.");
        when(messageService.get("wallet.invitation.canceled"))
                .thenReturn("Invitation canceled.");
        when(messageService.get("wallet.member.removed"))
                .thenReturn("Member removed.");
        when(messageService.get("wallet.left")).thenReturn("Wallet left.");
        when(request.getRequestURI()).thenReturn("/api/v1/wallets/action");

        ApiResponse<Void> rejected = controller.rejectInvitationById(
                invitationId, authentication, request);
        ApiResponse<Void> canceled = controller.cancelInvitation(
                invitationId, authentication, request);
        ApiResponse<Void> removed = controller.removeMember(
                walletId, memberId, authentication, request);
        ApiResponse<Void> left = controller.leaveWallet(
                walletId, authentication, request);

        assertTrue(rejected.isSuccess());
        assertTrue(canceled.isSuccess());
        assertTrue(removed.isSuccess());
        assertTrue(left.isSuccess());
        verify(serviceWalletSharing).rejectInvitationById(
                invitationId, userId);
        verify(serviceWalletSharing).cancelInvitation(invitationId, userId);
        verify(serviceWalletSharing).removeMember(
                walletId, userId, memberId);
        verify(serviceWalletSharing).leaveWallet(walletId, userId);
    }

    @Test
    void listsPendingAndSentInvitations() {
        UUID walletId = UUID.randomUUID();
        List<WalletInvitationResponse> pending = List.of();
        List<WalletInvitationSentResponse> sent = List.of();
        when(serviceWalletSharing.getPendingInvitations(userId))
                .thenReturn(pending);
        when(serviceWalletSharing.getSentInvitations(walletId, userId))
                .thenReturn(sent);
        when(messageService.get("wallet.invitations.pending.retrieved"))
                .thenReturn("Pending invitations retrieved.");
        when(messageService.get("wallet.invitations.sent.retrieved"))
                .thenReturn("Sent invitations retrieved.");
        when(request.getRequestURI()).thenReturn("/api/v1/wallets/invitations");

        ApiResponse<List<WalletInvitationResponse>> pendingResponse =
                controller.getPendingInvitations(authentication, request);
        ApiResponse<List<WalletInvitationSentResponse>> sentResponse =
                controller.getSentInvitations(walletId, authentication, request);

        assertTrue(pendingResponse.isSuccess());
        assertTrue(sentResponse.isSuccess());
        assertEquals(pending, pendingResponse.getData());
        assertEquals(sent, sentResponse.getData());
        verify(serviceWalletSharing).getPendingInvitations(userId);
        verify(serviceWalletSharing).getSentInvitations(walletId, userId);
    }
}
