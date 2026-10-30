package Diaz.Dev.BFlow.wallet.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.controllers.ControllerWallet;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.enums.WalletScope;
import bflow.wallet.service.ServiceWallet;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControllerWalletTest {

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private MessageService messageService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    private ControllerWallet controller;
    private UUID userId;

    @BeforeEach
    void setUp() {
        controller = new ControllerWallet(
                serviceWallet, currentUserService, messageService);
        userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId(authentication))
                .thenReturn(userId);
    }

    @Test
    void listingWalletsReturnsFilteredPageForTheAuthenticatedUser() {
        UUID walletId = UUID.randomUUID();
        WalletResponse wallet = WalletResponse.builder()
                .id(walletId)
                .name("Travel")
                .balance(new BigDecimal("125.50"))
                .role(WalletRole.OWNER)
                .build();
        PageRequest pageable = PageRequest.of(0, 10);
        PageImpl<WalletResponse> page = new PageImpl<>(
                List.of(wallet), pageable, 1);
        when(serviceWallet.getUserWallets(
                userId, "travel", WalletRole.OWNER, WalletScope.SHARED,
                pageable)).thenReturn(page);
        when(messageService.get("wallet.list.retrieved"))
                .thenReturn("Wallets retrieved.");
        when(request.getRequestURI()).thenReturn("/api/v1/wallets");

        ApiResponse<?> response = controller.getUserWallets(
                authentication, "travel", WalletRole.OWNER,
                WalletScope.SHARED, pageable, request);

        assertTrue(response.isSuccess());
        assertEquals("Wallets retrieved.", response.getMessage());
        assertEquals("/api/v1/wallets", response.getPath());
        PageImpl<?> returnedPage = (PageImpl<?>) response.getData();
        WalletResponse returnedWallet =
                (WalletResponse) returnedPage.getContent().get(0);
        assertEquals(walletId, returnedWallet.getId());
        assertEquals("Travel", returnedWallet.getName());
        assertEquals(new BigDecimal("125.50"), returnedWallet.getBalance());
        assertEquals(1, returnedPage.getTotalElements());
        verify(serviceWallet).getUserWallets(
                userId, "travel", WalletRole.OWNER, WalletScope.SHARED,
                pageable);
    }

    @Test
    void retrievingWalletReturnsItsBalanceAndIdentity() {
        UUID walletId = UUID.randomUUID();
        WalletResponse wallet = WalletResponse.builder()
                .id(walletId)
                .name("Emergency fund")
                .balance(new BigDecimal("850.00"))
                .build();
        when(serviceWallet.getWalletById(walletId, userId))
                .thenReturn(wallet);
        when(messageService.get("wallet.retrieved"))
                .thenReturn("Wallet retrieved.");
        when(request.getRequestURI())
                .thenReturn("/api/v1/wallets/" + walletId);

        ApiResponse<WalletResponse> response = controller.getWalletById(
                walletId, authentication, request);

        assertTrue(response.isSuccess());
        assertEquals("Wallet retrieved.", response.getMessage());
        assertEquals("/api/v1/wallets/" + walletId, response.getPath());
        assertEquals(walletId, response.getData().getId());
        assertEquals("Emergency fund", response.getData().getName());
        assertEquals(new BigDecimal("850.00"),
                response.getData().getBalance());
        verify(serviceWallet).getWalletById(walletId, userId);
    }
}
