package Diaz.Dev.BFlow.recurring.services;

import bflow.auth.entities.User;
import bflow.auth.services.UserService;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.i18n.MessageService;
import bflow.recurring.DTO.RecurringRequest;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringExecutionService;
import bflow.recurring.services.RecurringTransactionExecutor;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/** Tests initial scheduling for recurring transactions. */
@ExtendWith(MockitoExtension.class)
class RecurringExecutionServiceTest {

    @Mock private RepositoryRecurringTransaction repository;
    @Mock private RecurringTransactionExecutor executor;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private UserService userService;
    @Mock private RepositoryCategory repositoryCategory;
    @Mock private RepositoryWalletUser repositoryWalletUser;
    @Mock private PlanLimitService planLimitService;
    @Mock private MessageService messageService;

    @Test
    void createRecurring_withPastStartSchedulesNextFutureOccurrence() {
        UUID userId = UUID.randomUUID();
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, java.time.Clock.system(
                        java.time.ZoneId.of("America/El_Salvador")));
        RecurringRequest request = request(LocalDate.now().minusDays(10), null);
        WalletUser walletUser = walletUser(userId);

        doNothing().when(userService).validateUserActive(userId);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(repositoryWalletUser.findByWalletIdAndUserId(
                request.getWalletId(), userId)).thenReturn(Optional.of(walletUser));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(new Category()));
        when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringResponse response = service.createRecurring(request, userId);

        ArgumentCaptor<RecurringTransaction> captor =
                ArgumentCaptor.forClass(RecurringTransaction.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertFalse(captor.getValue().getNextExecutionDate()
                .isBefore(LocalDate.now()));
        assertTrue(captor.getValue().getActive());
        assertEquals(request.getStartDate(), response.getStartDate());
        assertNull(response.getEndDate());
    }

    @Test
    void createRecurring_withPastEndDateStartsInactive() {
        UUID userId = UUID.randomUUID();
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, java.time.Clock.system(
                        java.time.ZoneId.of("America/El_Salvador")));
        LocalDate start = LocalDate.now().minusMonths(2);
        RecurringRequest request = request(start, LocalDate.now().minusDays(1));
        WalletUser walletUser = walletUser(userId);

        doNothing().when(userService).validateUserActive(userId);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(repositoryWalletUser.findByWalletIdAndUserId(
                request.getWalletId(), userId)).thenReturn(Optional.of(walletUser));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(new Category()));
        when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createRecurring(request, userId);

        ArgumentCaptor<RecurringTransaction> captor =
                ArgumentCaptor.forClass(RecurringTransaction.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertFalse(captor.getValue().getActive());
    }

    @Test
    void createRecurring_weeklyPastStartSchedulesOnOrAfterToday() {
        UUID userId = UUID.randomUUID();
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, java.time.Clock.system(
                        java.time.ZoneId.of("America/El_Salvador")));
        RecurringRequest request = request(LocalDate.now().minusDays(10), null);
        request.setFrequency(RecurringFrequency.WEEKLY);
        WalletUser walletUser = walletUser(userId);

        doNothing().when(userService).validateUserActive(userId);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(repositoryWalletUser.findByWalletIdAndUserId(
                request.getWalletId(), userId)).thenReturn(Optional.of(walletUser));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(new Category()));
        when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createRecurring(request, userId);

        ArgumentCaptor<RecurringTransaction> captor =
                ArgumentCaptor.forClass(RecurringTransaction.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertFalse(captor.getValue().getNextExecutionDate()
                .isBefore(LocalDate.now()));
    }

    @Test
    void updateEndDate_nullMakesAnExistingRecurrenceIndefinite() {
        UUID userId = UUID.randomUUID();
        UUID recurringId = UUID.randomUUID();
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, java.time.Clock.system(
                        java.time.ZoneId.of("America/El_Salvador")));
        RecurringTransaction recurring = new RecurringTransaction();
        User owner = new User();
        owner.setId(userId);
        recurring.setUser(owner);
        recurring.setStartDate(LocalDate.now().minusMonths(1));
        recurring.setEndDate(LocalDate.now());
        recurring.setActive(false);
        when(repository.findById(recurringId)).thenReturn(Optional.of(recurring));

        service.updateEndDate(recurringId, userId, null);

        assertNull(recurring.getEndDate());
        assertFalse(recurring.getActive());
    }

    private RecurringRequest request(final LocalDate startDate,
                                     final LocalDate endDate) {
        RecurringRequest request = new RecurringRequest();
        request.setTitle("Rent");
        request.setAmount(BigDecimal.TEN);
        request.setWalletId(UUID.randomUUID());
        request.setCategoryId(UUID.randomUUID());
        request.setType(RecurringType.EXPENSE);
        request.setFrequency(RecurringFrequency.MONTHLY);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    private WalletUser walletUser(final UUID userId) {
        User user = new User();
        user.setId(userId);
        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        WalletUser walletUser = new WalletUser();
        walletUser.setUser(user);
        walletUser.setWallet(wallet);
        walletUser.setRole(WalletRole.OWNER);
        return walletUser;
    }
}
