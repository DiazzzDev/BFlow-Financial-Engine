package Diaz.Dev.BFlow.tranfers;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.i18n.MessageService;
import bflow.common.exception.ResourceNotFoundException;
import bflow.tranfers.DTO.TransferenceRequest;
import bflow.tranfers.RepositoryTransfers;
import bflow.tranfers.ServiceTransfers;
import bflow.tranfers.entities.Transfer;
import bflow.wallet.enums.Currency;
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
import static org.mockito.Mockito.times;
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
    private final UUID fromId = new UUID(0, 1);
    private final UUID toId = new UUID(0, 2);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        when(repositoryUser.findById(userId)).thenReturn(Optional.of(user));
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

    @Test
    void rejectsTransferWhenUserRecordIsMissing() {
        when(repositoryUser.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("10.00")), userId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repositoryWalletUser, never()).findByWalletIdAndUserId(any(), any());
        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void rejectsWhenOriginWalletCannotBeLocked() {
        when(repositoryWalletUser.findByWalletIdAndUserId(fromId, userId))
                .thenReturn(Optional.of(linkedWallet(fromId)));
        when(repositoryWalletUser.findByWalletIdAndUserId(toId, userId))
                .thenReturn(Optional.of(linkedWallet(toId)));
        when(repositoryWallet.findByIdForUpdate(fromId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("10.00")), userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repositoryWallet, times(1)).findByIdForUpdate(fromId);
        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void rejectsWhenDestinationWalletCannotBeLocked() {
        Wallet from = wallet(fromId, Currency.USD, new BigDecimal("100.00"));
        when(repositoryWalletUser.findByWalletIdAndUserId(fromId, userId))
                .thenReturn(Optional.of(linkedWallet(from)));
        when(repositoryWalletUser.findByWalletIdAndUserId(toId, userId))
                .thenReturn(Optional.of(linkedWallet(toId)));
        when(repositoryWallet.findByIdForUpdate(fromId))
                .thenReturn(Optional.of(from));
        when(repositoryWallet.findByIdForUpdate(toId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("10.00")), userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void rejectsTransferBetweenTheSameWallet() {
        Wallet sameWallet = wallet(fromId, Currency.USD,
                new BigDecimal("100.00"));
        when(repositoryWalletUser.findByWalletIdAndUserId(fromId, userId))
                .thenReturn(Optional.of(linkedWallet(sameWallet)));
        when(repositoryWallet.findByIdForUpdate(fromId))
                .thenReturn(Optional.of(sameWallet));

        assertThatThrownBy(() -> service.saveTransfer(
                request(fromId, fromId, new BigDecimal("10.00")), userId))
                .isInstanceOf(IllegalStateException.class);

        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void rejectsTransferBetweenDifferentCurrencies() {
        Wallet from = wallet(fromId, Currency.USD, new BigDecimal("100.00"));
        Wallet to = wallet(toId, Currency.EUR, new BigDecimal("20.00"));
        stubAccessibleWallets(from, to);

        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("10.00")), userId))
                .isInstanceOf(IllegalStateException.class);

        verify(serviceWallet, never()).subtractBalance(any(), any());
        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void rejectsTransferThatExceedsSourceBalance() {
        Wallet from = wallet(fromId, Currency.USD, new BigDecimal("9.99"));
        Wallet to = wallet(toId, Currency.USD, new BigDecimal("20.00"));
        stubAccessibleWallets(from, to);

        assertThatThrownBy(() -> service.saveTransfer(
                request(new BigDecimal("10.00")), userId))
                .isInstanceOf(IllegalStateException.class);

        verify(serviceWallet, never()).subtractBalance(any(), any());
        verify(repositoryTransfers, never()).save(any());
    }

    @Test
    void completesTransferAndPersistsItsDetails() {
        Wallet from = wallet(fromId, Currency.USD, new BigDecimal("100.00"));
        Wallet to = wallet(toId, Currency.USD, new BigDecimal("20.00"));
        stubAccessibleWallets(from, to);
        when(repositoryTransfers.save(any(Transfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveTransfer(request(new BigDecimal("10.00")), userId);

        verify(serviceWallet).subtractBalance(from, new BigDecimal("10.00"));
        verify(serviceWallet).addBalance(to, new BigDecimal("10.00"));
        verify(repositoryTransfers).save(org.mockito.ArgumentMatchers.argThat(
                transfer -> transfer.getFromWallet() == from
                        && transfer.getToWallet() == to
                        && transfer.getUser() == user
                        && transfer.getStatus().name().equals("COMPLETED")
        ));
    }

    @Test
    void locksWalletsInAscendingIdOrderAndKeepsRequestDirection() {
        UUID lowId = new UUID(0, 1);
        UUID highId = new UUID(0, 2);
        Wallet source = wallet(highId, Currency.USD, new BigDecimal("100.00"));
        Wallet destination = wallet(lowId, Currency.USD,
                new BigDecimal("20.00"));
        when(repositoryWalletUser.findByWalletIdAndUserId(highId, userId))
                .thenReturn(Optional.of(linkedWallet(source)));
        when(repositoryWalletUser.findByWalletIdAndUserId(lowId, userId))
                .thenReturn(Optional.of(linkedWallet(destination)));
        when(repositoryWallet.findByIdForUpdate(lowId))
                .thenReturn(Optional.of(destination));
        when(repositoryWallet.findByIdForUpdate(highId))
                .thenReturn(Optional.of(source));
        when(repositoryTransfers.save(any(Transfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveTransfer(
                request(highId, lowId, new BigDecimal("10.00")), userId);

        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(repositoryWallet);
        lockOrder.verify(repositoryWallet).findByIdForUpdate(lowId);
        lockOrder.verify(repositoryWallet).findByIdForUpdate(highId);
        verify(serviceWallet).subtractBalance(source, new BigDecimal("10.00"));
        verify(serviceWallet).addBalance(destination, new BigDecimal("10.00"));
    }

    private TransferenceRequest request(BigDecimal amount) {
        return request(fromId, toId, amount);
    }

    private TransferenceRequest request(
            UUID sourceId,
            UUID destinationId,
            BigDecimal amount
    ) {
        TransferenceRequest request = new TransferenceRequest();
        request.setFromWalletId(sourceId);
        request.setToWalletId(destinationId);
        request.setAmount(amount);
        request.setDescription("test transfer");
        return request;
    }

    private WalletUser linkedWallet(UUID id) {
        return linkedWallet(wallet(id, Currency.USD, BigDecimal.ZERO));
    }

    private WalletUser linkedWallet(Wallet wallet) {
        WalletUser linked = new WalletUser();
        linked.setWallet(wallet);
        return linked;
    }

    private Wallet wallet(
            UUID id,
            Currency currency,
            BigDecimal balance
    ) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setName("Wallet " + id);
        wallet.setCurrency(currency);
        wallet.setBalance(balance);
        return wallet;
    }

    private void stubAccessibleWallets(Wallet from, Wallet to) {
        when(repositoryWalletUser.findByWalletIdAndUserId(fromId, userId))
                .thenReturn(Optional.of(linkedWallet(from)));
        when(repositoryWalletUser.findByWalletIdAndUserId(toId, userId))
                .thenReturn(Optional.of(linkedWallet(to)));
        when(repositoryWallet.findByIdForUpdate(fromId))
                .thenReturn(Optional.of(from));
        when(repositoryWallet.findByIdForUpdate(toId))
                .thenReturn(Optional.of(to));
    }
}
