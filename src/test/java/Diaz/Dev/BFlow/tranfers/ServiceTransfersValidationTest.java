package Diaz.Dev.BFlow.tranfers;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.i18n.MessageService;
import bflow.tranfers.DTO.TransferenceRequest;
import bflow.tranfers.RepositoryTransfers;
import bflow.tranfers.ServiceTransfers;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTransfersValidationTest {

    @Mock private RepositoryTransfers repositoryTransfers;
    @Mock private RepositoryWalletUser repositoryWalletUser;
    @Mock private RepositoryWallet repositoryWallet;
    @Mock private RepositoryUser repositoryUser;
    @Mock private ServiceWallet serviceWallet;
    @Mock private UserService userService;
    @Mock private MessageService messageService;
    @InjectMocks private ServiceTransfers service;

    private final UUID userId = UUID.randomUUID();
    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
        when(messageService.get(anyString())).thenReturn("validation error");
    }

    @Test
    void rejectsZeroAmountBeforeLookingUpWallets() {
        assertThatThrownBy(() -> service.saveTransfer(request(BigDecimal.ZERO), userId))
                .isInstanceOf(IllegalStateException.class);

        verify(repositoryWalletUser, never()).findByWalletIdAndUserId(any(), any());
        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void rejectsNegativeAmountBeforeLookingUpWallets() {
        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("-0.01")), userId))
                .isInstanceOf(IllegalStateException.class);

        verify(repositoryWalletUser, never()).findByWalletIdAndUserId(any(), any());
    }

    @Test
    void rejectsWhenUserCannotAccessSourceWallet() {
        when(repositoryWalletUser.findByWalletIdAndUserId(fromId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("10.00")), userId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repositoryWalletUser, never()).findByWalletIdAndUserId(toId, userId);
        verify(repositoryWallet, never()).findByIdForUpdate(any());
        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void rejectsWhenUserCannotAccessDestinationWallet() {
        when(repositoryWalletUser.findByWalletIdAndUserId(fromId, userId))
                .thenReturn(Optional.of(linkedWallet(fromId)));
        when(repositoryWalletUser.findByWalletIdAndUserId(toId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("10.00")), userId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repositoryWallet, never()).findByIdForUpdate(any());
        verify(repositoryTransfers, never()).save(any());
    }

    private TransferenceRequest request(BigDecimal amount) {
        TransferenceRequest request = new TransferenceRequest();
        request.setFromWalletId(fromId);
        request.setToWalletId(toId);
        request.setAmount(amount);
        request.setDescription("test transfer");
        return request;
    }

    private WalletUser linkedWallet(UUID id) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        WalletUser linked = new WalletUser();
        linked.setWallet(wallet);
        return linked;
    }
}
