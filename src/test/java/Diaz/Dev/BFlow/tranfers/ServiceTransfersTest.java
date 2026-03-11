package Diaz.Dev.BFlow.tranfers;

import bflow.auth.entities.User;
import bflow.auth.enums.UserStatus;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.common.exception.ResourceNotFoundException;
import bflow.tranfers.DTO.TransferenceRequest;
import bflow.tranfers.DTO.TransferenceResponse;
import bflow.tranfers.RepositoryTransfers;
import bflow.tranfers.ServiceTransfers;
import bflow.tranfers.entities.Transfer;
import bflow.tranfers.enums.TransferStatus;
import bflow.wallet.RepositoryWallet;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.ServiceWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ServiceTransfers.
 */
@ExtendWith(MockitoExtension.class)
class ServiceTransfersTest {

    @Mock
    private RepositoryTransfers repositoryTransfers;

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private RepositoryWalletUser repositoryWalletUser;

    @Mock
    private RepositoryUser repositoryUser;

    @Mock
    private ServiceWallet serviceWallet;

    @Mock
    private UserServiceImpl userService;

    @InjectMocks
    private ServiceTransfers serviceTransfers;

    private UUID userId;
    private UUID transferId;
    private User user;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        transferId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setStatus(UserStatus.ACTIVE);

        Wallet wallet1 = new Wallet();
        wallet1.setId(UUID.randomUUID());
        wallet1.setName("From Wallet");
        wallet1.setBalance(BigDecimal.valueOf(1000));

        Wallet wallet2 = new Wallet();
        wallet2.setId(UUID.randomUUID());
        wallet2.setName("To Wallet");
        wallet2.setBalance(BigDecimal.valueOf(500));

        transfer = new Transfer();
        transfer.setId(transferId);
        transfer.setFromWalletId(wallet1);
        transfer.setToWalletId(wallet2);
        transfer.setAmount(BigDecimal.valueOf(100));
        transfer.setDescription("Test transfer");
        transfer.setUser(user);
        transfer.setStatus(TransferStatus.COMPLETED);
    }

    @Test
    void testGetTransferById() {
        // Arrange
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryTransfers.findByIdAndUserId(transferId, userId))
                .thenReturn(Optional.of(transfer));

        // Act
        TransferenceResponse result = serviceTransfers.getTransferById(transferId, userId);

        // Assert
        assertNotNull(result);
        assertEquals(transferId, result.getId());
        assertEquals(BigDecimal.valueOf(100), result.getAmount());
        verify(userService).validateUserActive(userId);
    }

    @Test
    void testGetTransferByIdNotFound() {
        // Arrange
        doNothing().when(userService).validateUserActive(userId);
        when(repositoryTransfers.findByIdAndUserId(transferId, userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> serviceTransfers.getTransferById(transferId, userId));
    }

    @Test
    void testGetUserTransfers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Transfer> transfers = List.of(transfer);
        Page<Transfer> transferPage = new PageImpl<>(transfers, pageable, 1);

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryTransfers.findByUserId(userId, pageable)).thenReturn(transferPage);

        // Act
        Page<TransferenceResponse> result = serviceTransfers.getUserTransfers(userId, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(transferId, result.getContent().get(0).getId());
        verify(userService).validateUserActive(userId);
    }

    @Test
    void testGetUserTransfersByWalletId() {
        // Arrange
        UUID walletId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        List<Transfer> transfers = List.of(transfer);
        Page<Transfer> transferPage = new PageImpl<>(transfers, pageable, 1);

        doNothing().when(userService).validateUserActive(userId);
        when(repositoryTransfers.findTransfersByWallet(userId, walletId, pageable))
                .thenReturn(transferPage);

        // Act
        Page<TransferenceResponse> result =
                serviceTransfers.getUserTransfersByWalletId(userId, walletId, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        verify(userService).validateUserActive(userId);
    }
}
