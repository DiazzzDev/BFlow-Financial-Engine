package Diaz.Dev.BFlow.wallet.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.wallet.DTO.WalletInvitationRequest;
import bflow.wallet.DTO.WalletInvitationResponse;
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
}
