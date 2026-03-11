package Diaz.Dev.BFlow.wallet;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.wallet.DTO.WalletRequest;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.RepositoryWallet;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.ServiceWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ServiceWallet.
 */
@ExtendWith(MockitoExtension.class)
class ServiceWalletTest {

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private UserServiceImpl userService;

    @InjectMocks
    private ServiceWallet serviceWallet;

    private UUID userId;
    private UUID walletId;
    private User user;
    private Wallet wallet;
    private WalletUser walletUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setStatus(UserStatus.ACTIVE);

        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Test Wallet");
        wallet.setDescription("Test description");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(BigDecimal.valueOf(1000));
        wallet.setInitialValue(BigDecimal.valueOf(1000));
        wallet.setCreatedAt(Instant.now());

        walletUser = new WalletUser();
        walletUser.setUser(user);
        walletUser.setWallet(wallet);
        walletUser.setRole(WalletRole.OWNER);
    }

    @Test
    void testGetUserWallets() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<WalletUser> walletUsers = List.of(walletUser);
        Page<WalletUser> walletPage = new PageImpl<>(walletUsers, pageable, 1);

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByUserId(userId, pageable)).thenReturn(walletPage);

        // Act
        Page<WalletResponse> result = serviceWallet.getUserWallets(userId, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Wallet", result.getContent().get(0).getName());
        verify(userService).validateUserActive(userId);
    }

    @Test
    void testGetWalletById() {
        // Arrange
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(walletUser));

        // Act
        WalletResponse result = serviceWallet.getWalletById(walletId, userId);

        // Assert
        assertEquals(walletId, result.getId());
        assertEquals("Test Wallet", result.getName());
        verify(repositoryWalletUser).findByWalletIdAndUserId(walletId, userId);
    }

    @Test
    void testGetWalletByIdAccessDenied() {
        // Arrange
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> serviceWallet.getWalletById(walletId, userId));
    }

    @Test
    void testPatchWalletAccessDenied() {
        // Arrange
        WalletUser memberUser = new WalletUser();
        memberUser.setUser(user);
        memberUser.setWallet(wallet);
        memberUser.setRole(WalletRole.MEMBER);

        WalletRequest request = new WalletRequest();
        request.setName("New Name");

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(memberUser));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> serviceWallet.patchWallet(walletId, request, userId));
    }
}
