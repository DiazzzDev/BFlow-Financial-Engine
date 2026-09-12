package Diaz.Dev.BFlow.wallet;

import bflow.common.exception.ResourceNotFoundException;
import bflow.common.i18n.MessageService;
import bflow.wallet.DTO.WalletPair;
import bflow.wallet.entities.Wallet;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.service.WalletLockService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WalletLockService, covering the deterministic
 * lock-ordering logic used to avoid deadlocks between concurrent
 * transfers touching the same pair of wallets.
 */
@ExtendWith(MockitoExtension.class)
class WalletLockServiceTest {

    @Mock
    private RepositoryWallet repositoryWallet;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private WalletLockService walletLockService;

    private UUID lowerId;
    private UUID higherId;
    private Wallet lowerWallet;
    private Wallet higherWallet;

    @BeforeEach
    void setUp() {
        lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        lowerWallet = new Wallet();
        lowerWallet.setId(lowerId);

        higherWallet = new Wallet();
        higherWallet.setId(higherId);

        lenient().when(messageService.get("wallet.notFound"))
                .thenReturn("Wallet not found.");
        lenient().when(messageService.get("wallet.origin.notFound"))
                .thenReturn("Origin wallet not found.");
        lenient().when(messageService.get("wallet.target.notFound"))
                .thenReturn("Target wallet not found.");
    }

    @Test
    void lockWallets_sameWalletId_returnsSameInstanceForBothAndLocksOnce() {
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.of(lowerWallet));

        WalletPair result = walletLockService.lockWallets(lowerId, lowerId);

        assertSame(lowerWallet, result.oldWallet());
        assertSame(lowerWallet, result.newWallet());
        verify(repositoryWallet, times(1)).findByIdForUpdate(lowerId);
    }

    @Test
    void lockWallets_ascendingOrder_locksOriginThenTarget() {
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.of(lowerWallet));
        when(repositoryWallet.findByIdForUpdate(higherId))
                .thenReturn(Optional.of(higherWallet));

        WalletPair result = walletLockService.lockWallets(lowerId, higherId);

        assertSame(lowerWallet, result.oldWallet());
        assertSame(higherWallet, result.newWallet());
    }

    @Test
    void lockWallets_descendingOrder_stillReturnsOriginAndTargetInRequestOrder() {
        when(repositoryWallet.findByIdForUpdate(higherId))
                .thenReturn(Optional.of(higherWallet));
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.of(lowerWallet));

        WalletPair result = walletLockService.lockWallets(higherId, lowerId);

        assertSame(higherWallet, result.oldWallet());
        assertSame(lowerWallet, result.newWallet());
    }

    @Test
    void lockWallets_sameWalletId_originNotFound_throwsWithGenericMessage() {
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> walletLockService.lockWallets(lowerId, lowerId));

        assertEquals("Wallet not found.", ex.getMessage());
    }

    @Test
    void lockWallets_ascendingOrder_originNotFound_throwsOriginMessage() {
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> walletLockService.lockWallets(lowerId, higherId));

        assertEquals("Origin wallet not found.", ex.getMessage());
        verify(repositoryWallet, never()).findByIdForUpdate(higherId);
    }

    @Test
    void lockWallets_ascendingOrder_targetNotFound_throwsTargetMessage() {
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.of(lowerWallet));
        when(repositoryWallet.findByIdForUpdate(higherId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> walletLockService.lockWallets(lowerId, higherId));

        assertEquals("Target wallet not found.", ex.getMessage());
    }

    @Test
    void lockWallets_descendingOrder_targetNotFound_throwsTargetMessage() {
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> walletLockService.lockWallets(higherId, lowerId));

        assertEquals("Target wallet not found.", ex.getMessage());
        verify(repositoryWallet, never()).findByIdForUpdate(higherId);
    }

    @Test
    void lockWallets_descendingOrder_originNotFound_throwsOriginMessage() {
        when(repositoryWallet.findByIdForUpdate(lowerId))
                .thenReturn(Optional.of(lowerWallet));
        when(repositoryWallet.findByIdForUpdate(higherId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> walletLockService.lockWallets(higherId, lowerId));

        assertEquals("Origin wallet not found.", ex.getMessage());
    }
}
