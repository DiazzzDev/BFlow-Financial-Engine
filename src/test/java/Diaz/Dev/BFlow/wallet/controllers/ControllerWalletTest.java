package Diaz.Dev.BFlow.wallet.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.income.DTO.IncomeResponse;
import bflow.wallet.DTO.OpeningBalanceRequest;
import bflow.wallet.DTO.UpdateWalletRequest;
import bflow.wallet.DTO.WalletInfoResponse;
import bflow.wallet.DTO.WalletMemberResponse;
import bflow.wallet.DTO.WalletRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void retrievingWalletInfoReturnsServiceSummary() {
        UUID walletId = UUID.randomUUID();
        WalletInfoResponse info = new WalletInfoResponse(
                null, null, 0, BigDecimal.ZERO, List.of());
        when(serviceWallet.getWalletInfo(walletId, userId)).thenReturn(info);
        when(messageService.get("wallet.info.retrieved"))
                .thenReturn("Wallet information retrieved.");
        when(request.getRequestURI()).thenReturn(
                "/api/v1/wallets/" + walletId + "/info");

        ApiResponse<WalletInfoResponse> response = controller.getWalletInfo(
                walletId, authentication, request);

        assertTrue(response.isSuccess());
        assertSame(info, response.getData());
        assertEquals("Wallet information retrieved.", response.getMessage());
        verify(serviceWallet).getWalletInfo(walletId, userId);
    }

    @Test
    void retrievingWalletExpensesAndIncomesUsesRequestedPage() {
        UUID walletId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(1, 5);
        PageImpl<ExpenseResponse> expenses =
                new PageImpl<>(List.of(), pageable, 0);
        PageImpl<IncomeResponse> incomes =
                new PageImpl<>(List.of(), pageable, 0);
        when(serviceWallet.getWalletExpenses(walletId, userId, pageable))
                .thenReturn(expenses);
        when(serviceWallet.getWalletIncomes(walletId, userId, pageable))
                .thenReturn(incomes);
        when(messageService.get("wallet.expenses.retrieved"))
                .thenReturn("Expenses retrieved.");
        when(messageService.get("wallet.incomes.retrieved"))
                .thenReturn("Incomes retrieved.");
        when(request.getRequestURI())
                .thenReturn("/api/v1/wallets/" + walletId + "/expenses")
                .thenReturn("/api/v1/wallets/" + walletId + "/incomes");

        ApiResponse<?> expenseResponse = controller.getWalletExpenses(
                walletId, pageable, authentication, request);
        ApiResponse<?> incomeResponse = controller.getWalletIncomes(
                walletId, pageable, authentication, request);

        assertSame(expenses, expenseResponse.getData());
        assertSame(incomes, incomeResponse.getData());
        assertEquals("Expenses retrieved.", expenseResponse.getMessage());
        assertEquals("Incomes retrieved.", incomeResponse.getMessage());
        verify(serviceWallet).getWalletExpenses(walletId, userId, pageable);
        verify(serviceWallet).getWalletIncomes(walletId, userId, pageable);
    }

    @Test
    void retrievingWalletMembersReturnsMemberList() {
        UUID walletId = UUID.randomUUID();
        List<WalletMemberResponse> members = List.of(
                new WalletMemberResponse(
                        UUID.randomUUID(), "member@example.com", "Member",
                        null, WalletRole.MEMBER, null));
        when(serviceWallet.getWalletMembers(walletId, userId))
                .thenReturn(members);
        when(messageService.get("wallet.members.retrieved"))
                .thenReturn("Members retrieved.");
        when(request.getRequestURI())
                .thenReturn("/api/v1/wallets/" + walletId + "/members");

        ApiResponse<List<WalletMemberResponse>> response =
                controller.getWalletMembers(walletId, authentication, request);

        assertSame(members, response.getData());
        assertEquals("Members retrieved.", response.getMessage());
        verify(serviceWallet).getWalletMembers(walletId, userId);
    }

    @Test
    void settingOpeningBalanceReturnsUpdatedWallet() {
        UUID walletId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250.00");
        WalletResponse wallet = WalletResponse.builder()
                .id(walletId)
                .balance(amount)
                .build();
        when(serviceWallet.setOpeningBalance(walletId, amount, userId))
                .thenReturn(wallet);
        when(messageService.get("wallet.openingBalance.updated"))
                .thenReturn("Opening balance updated.");
        when(request.getRequestURI())
                .thenReturn("/api/v1/wallets/" + walletId + "/opening-balance");

        ApiResponse<WalletResponse> response = controller.setOpeningBalance(
                walletId, new OpeningBalanceRequest(amount),
                authentication, request);

        assertSame(wallet, response.getData());
        assertEquals("Opening balance updated.", response.getMessage());
        verify(serviceWallet).setOpeningBalance(walletId, amount, userId);
    }

    @Test
    void settingDefaultWalletAndDeletingWalletReturnNoContent() {
        UUID walletId = UUID.randomUUID();

        ResponseEntity<Void> defaultResponse = controller.setDefaultWallet(
                walletId, authentication);
        ResponseEntity<Void> deleteResponse = controller.deleteWallet(
                walletId, authentication);

        assertEquals(HttpStatus.NO_CONTENT, defaultResponse.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
        verify(serviceWallet).setDefaultWallet(walletId, userId);
        verify(serviceWallet).deleteWallet(walletId, userId);
    }

    @Test
    void creatingAndPatchingWalletReturnCreatedLocation() {
        UUID walletId = UUID.randomUUID();
        WalletRequest createRequest = new WalletRequest();
        UpdateWalletRequest patchRequest = new UpdateWalletRequest();
        WalletResponse wallet = WalletResponse.builder()
                .id(walletId)
                .name("Travel")
                .build();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setScheme("https");
        servletRequest.setServerName("api.example.com");
        servletRequest.setServerPort(443);
        servletRequest.setRequestURI("/api/v1/wallets");
        when(serviceWallet.createWallet(createRequest, userId))
                .thenReturn(wallet);
        when(serviceWallet.patchWallet(walletId, patchRequest, userId))
                .thenReturn(wallet);
        when(messageService.get("wallet.created")).thenReturn("Wallet created.");
        when(messageService.get("wallet.updated")).thenReturn("Wallet updated.");

        ResponseEntity<ApiResponse<WalletResponse>> created =
                controller.createWallet(
                        createRequest, authentication, servletRequest);
        ResponseEntity<ApiResponse<WalletResponse>> patched =
                controller.patchWallet(
                        walletId, patchRequest, authentication, servletRequest);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.CREATED, patched.getStatusCode());
        URI expectedLocation = URI.create(
                "https://api.example.com/api/v1/wallets/" + walletId);
        assertEquals(expectedLocation,
                created.getHeaders().getLocation());
        assertEquals(expectedLocation,
                patched.getHeaders().getLocation());
        assertEquals("Wallet created.", created.getBody().getMessage());
        assertEquals("Wallet updated.", patched.getBody().getMessage());
        verify(serviceWallet).createWallet(createRequest, userId);
        verify(serviceWallet).patchWallet(
                walletId, patchRequest, userId);
    }
}
