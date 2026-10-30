package Diaz.Dev.BFlow.recurring.services;

import bflow.auth.entities.User;
import bflow.auth.enums.SupportedLanguage;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;

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
    void executeDueTransactions_recordsFailureAndEmailsOwner() {
        UUID id = UUID.randomUUID();
        RecurringTransaction due = new RecurringTransaction();
        due.setId(id);
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),
                ZoneId.of("America/El_Salvador"));
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, clock);
        RecurringTransactionExecutor.FailureNotification notification =
                new RecurringTransactionExecutor.FailureNotification(
                        "owner@example.com", "Owner", "Rent",
                        BigDecimal.TEN, 2, false, "Insufficient balance",
                        SupportedLanguage.ES);
        when(repository.findDueTransactions(LocalDate.of(2026, 10, 5)))
                .thenReturn(java.util.List.of(due));
        doThrow(new IllegalArgumentException("Insufficient balance"))
                .when(executor).executeSingle(id);
        when(executor.recordFailure(eq(id), any(IllegalArgumentException.class)))
                .thenReturn(notification);

        service.executeDueTransactions();

        verify(emailTemplateService).sendRecurringFailedEmail(
                "owner@example.com", "Owner", "Rent", BigDecimal.TEN,
                2, false, "Insufficient balance", SupportedLanguage.ES);
    }

    @Test
    void executeDueTransactions_mailFailureDoesNotStopBatch() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        RecurringTransaction first = new RecurringTransaction();
        first.setId(firstId);
        RecurringTransaction second = new RecurringTransaction();
        second.setId(secondId);
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),
                ZoneId.of("America/El_Salvador"));
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, clock);
        when(repository.findDueTransactions(LocalDate.of(2026, 10, 5)))
                .thenReturn(java.util.List.of(first, second));
        doThrow(new IllegalStateException("failed"))
                .when(executor).executeSingle(firstId);
        when(executor.recordFailure(eq(firstId), any(IllegalStateException.class)))
                .thenReturn(new RecurringTransactionExecutor.FailureNotification(
                        "owner@example.com", "Owner", "Rent", BigDecimal.TEN,
                        1, false, "failed", SupportedLanguage.ES));
        org.mockito.Mockito.doThrow(new RuntimeException("email down"))
                .when(emailTemplateService).sendRecurringFailedEmail(
                        any(), any(), any(), any(), anyInt(), anyBoolean(),
                        any(), any());

        service.executeDueTransactions();

        verify(executor).executeSingle(secondId);
    }

    @Test
    void createRecurring_withPastStartSchedulesNextFutureOccurrence() {
        UUID userId = UUID.randomUUID();
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, java.time.Clock.system(
                        java.time.ZoneId.of("America/El_Salvador")));
        RecurringRequest request = request(LocalDate.now().minusDays(10), null);
        request.setDescription("Monthly apartment rent");
        request.setAmount(new BigDecimal("1250.50"));
        WalletUser walletUser = walletUser(userId);
        Category savedCategory = new Category();
        savedCategory.setId(request.getCategoryId());
        Wallet expectedWallet = walletUser.getWallet();

        doNothing().when(userService).validateUserActive(userId);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(repositoryWalletUser.findByWalletIdAndUserId(
                request.getWalletId(), userId)).thenReturn(Optional.of(walletUser));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(savedCategory));
        when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringResponse response = service.createRecurring(request, userId);

        ArgumentCaptor<RecurringTransaction> captor =
                ArgumentCaptor.forClass(RecurringTransaction.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertFalse(captor.getValue().getNextExecutionDate()
                .isBefore(LocalDate.now()));
        assertEquals(request.getTitle(), captor.getValue().getTitle());
        assertEquals(request.getDescription(), captor.getValue().getDescription());
        assertEquals(request.getAmount(), captor.getValue().getAmount());
        assertEquals(request.getType(), captor.getValue().getType());
        assertEquals(request.getFrequency(), captor.getValue().getFrequency());
        assertEquals(request.getIntervalValue(),
                captor.getValue().getIntervalValue());
        assertEquals(request.getStartDate(), captor.getValue().getStartDate());
        assertEquals(request.getEndDate(), captor.getValue().getEndDate());
        assertEquals(expectedWallet, captor.getValue().getWallet());
        assertEquals(savedCategory, captor.getValue().getCategory());
        assertEquals(userId, captor.getValue().getUser().getId());
        assertTrue(captor.getValue().getActive());
        assertEquals(request.getStartDate(), response.getStartDate());
        assertNull(response.getEndDate());
        verify(planLimitService).assertCanCreate(
                userId,
                bflow.subscription.FeatureCodes.RECURRING_TRANSACTIONS,
                0L);
    }

    @Test
    void createRecurring_pastDailyStartAdvancesByWholeIntervals() {
        UUID userId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),
                ZoneId.of("America/El_Salvador"));
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, clock);
        RecurringRequest request = request(LocalDate.of(2026, 9, 29), null);
        request.setFrequency(RecurringFrequency.DAILY);
        request.setIntervalValue(3);
        WalletUser walletUser = walletUser(userId);
        Category category = new Category();
        doNothing().when(userService).validateUserActive(userId);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(repositoryWalletUser.findByWalletIdAndUserId(
                request.getWalletId(), userId)).thenReturn(Optional.of(walletUser));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(category));
        when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createRecurring(request, userId);

        ArgumentCaptor<RecurringTransaction> captor =
                ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(repository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 10, 5),
                captor.getValue().getNextExecutionDate());
    }

    @Test
    void createRecurring_pendingDeletionUserIsRejectedBeforePlanChecks() {
        UUID userId = UUID.randomUUID();
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, Clock.system(
                        ZoneId.of("America/El_Salvador")));
        RecurringRequest request = request(LocalDate.now(), null);
        doThrow(new IllegalStateException("inactive"))
                .when(userService).validateUserActive(userId);

        assertThrows(IllegalStateException.class,
                () -> service.createRecurring(request, userId));

        verify(planLimitService, never()).assertCanCreate(
                any(), any(), anyLong());
        verify(repository, never()).countByUserIdAndActiveTrue(any());
        verify(repositoryWalletUser, never())
                .findByWalletIdAndUserId(any(), any());
        verify(repository, never()).save(any(RecurringTransaction.class));
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
