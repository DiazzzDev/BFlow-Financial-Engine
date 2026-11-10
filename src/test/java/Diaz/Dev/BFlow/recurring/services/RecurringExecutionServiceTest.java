package Diaz.Dev.BFlow.recurring.services;

import bflow.auth.entities.User;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.services.UserService;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
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
        savedCategory.setType(CategoryType.EXPENSE);
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
    void createRecurring_withoutCategoryPersistsUncategorizedRecurrence() {
        UUID userId = UUID.randomUUID();
        RecurringExecutionService service = new RecurringExecutionService(
                repository, executor, emailTemplateService, userService,
                repositoryCategory, repositoryWalletUser, planLimitService,
                messageService, Clock.system(ZoneId.of("America/El_Salvador")));
        RecurringRequest request = request(LocalDate.now(), null);
        request.setCategoryId(null);
        WalletUser walletUser = walletUser(userId);

        doNothing().when(userService).validateUserActive(userId);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(repositoryWalletUser.findByWalletIdAndUserId(
                request.getWalletId(), userId)).thenReturn(Optional.of(walletUser));
        when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringResponse response = service.createRecurring(request, userId);

        ArgumentCaptor<RecurringTransaction> captor =
                ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getCategory());
        assertNull(response.getCategoryId());
        verify(repositoryCategory, never()).findById(any());
    }

    @Test
    void createRecurring_defaultsWalletAndStartDateWhenOmitted() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 11, 10);
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"),
                ZoneId.of("America/El_Salvador"));
        RecurringExecutionService service = serviceWithClock(clock);
        RecurringRequest request = request(null, null);
        request.setWalletId(null);
        request.setCategoryId(null);
        WalletUser defaultWallet = walletUser(userId);

        when(repositoryWalletUser
                .findFirstByUserIdAndRoleOrderByDefaultWalletDescCreatedAtAsc(
                        userId, WalletRole.OWNER))
                .thenReturn(Optional.of(defaultWallet));
        when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecurringResponse response = service.createRecurring(request, userId);

        ArgumentCaptor<RecurringTransaction> saved =
                ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(repository).save(saved.capture());
        assertEquals(defaultWallet.getWallet(), saved.getValue().getWallet());
        assertEquals(today, saved.getValue().getStartDate());
        assertNull(saved.getValue().getCategory());
        assertEquals(today, response.getStartDate());
        verify(repositoryWalletUser, never())
                .findByWalletIdAndUserId(any(), eq(userId));
        verify(repositoryCategory, never()).findById(any());
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
        category.setType(CategoryType.EXPENSE);
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
        Category category = new Category();
        category.setType(CategoryType.EXPENSE);
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(category));
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
        Category category = new Category();
        category.setType(CategoryType.EXPENSE);
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(category));
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

    @Test
    void getWalletRecurring_checksWalletPermissionAndReturnsAllStatuses() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        WalletUser membership = walletUser(userId);
        membership.getWallet().setId(walletId);
        RecurringExecutionService service = serviceWithClock(
                Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),
                        ZoneId.of("America/El_Salvador")));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.of(membership));
        when(repository.findByWalletIdOrderByNextExecutionDateAsc(walletId))
                .thenReturn(java.util.List.of());

        assertTrue(service.getWalletRecurring(walletId, userId).isEmpty());

        verify(repository).findByWalletIdOrderByNextExecutionDateAsc(walletId);
    }

    @Test
    void getWalletRecurring_deniesNonMemberWithoutQueryingRecurrences() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        RecurringExecutionService service = serviceWithClock(Clock.system(
                ZoneId.of("America/El_Salvador")));
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, userId))
                .thenReturn(Optional.empty());

        assertThrows(bflow.common.exception.WalletAccessDeniedException.class,
                () -> service.getWalletRecurring(walletId, userId));

        verify(repository, never())
                .findByWalletIdOrderByNextExecutionDateAsc(any());
    }

    @Test
    void getRecurringById_returnsOwnedRecurrence() {
        UUID userId = UUID.randomUUID();
        UUID recurringId = UUID.randomUUID();
        RecurringExecutionService service = serviceWithClock(Clock.system(
                ZoneId.of("America/El_Salvador")));
        RecurringTransaction recurring = recurrence(userId,
                RecurringFrequency.MONTHLY, LocalDate.now(), null);
        recurring.setId(recurringId);
        when(repository.findById(recurringId)).thenReturn(Optional.of(recurring));

        RecurringResponse response = service.getRecurringById(recurringId, userId);

        assertEquals(recurringId, response.getId());
        assertNull(response.getEndDate());
    }

    @Test
    void getRecurringById_deniesAnotherUsersRecurrence() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID recurringId = UUID.randomUUID();
        RecurringExecutionService service = serviceWithClock(Clock.system(
                ZoneId.of("America/El_Salvador")));
        RecurringTransaction recurring = recurrence(ownerId,
                RecurringFrequency.MONTHLY, LocalDate.now(), null);
        when(repository.findById(recurringId)).thenReturn(Optional.of(recurring));

        assertThrows(bflow.common.exception.WalletAccessDeniedException.class,
                () -> service.getRecurringById(recurringId, otherUserId));
    }

    @Test
    void updateRecurring_allowsIndefiniteSalaryAndPreservesNextRunAndPausedState() {
        UUID userId = UUID.randomUUID();
        UUID recurringId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),
                ZoneId.of("America/El_Salvador"));
        RecurringExecutionService service = serviceWithClock(clock);
        RecurringTransaction current = recurrence(userId, RecurringFrequency.MONTHLY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 1));
        current.setId(recurringId);
        current.setNextExecutionDate(LocalDate.of(2026, 11, 1));
        current.setActive(false);
        RecurringRequest request = request(current.getStartDate(), null);
        request.setType(RecurringType.INCOME);
        request.setFrequency(current.getFrequency());
        request.setAmount(new BigDecimal("2000.00"));
        WalletUser membership = walletUser(userId);
        membership.getWallet().setId(request.getWalletId());
        Category category = new Category();
        category.setType(CategoryType.INCOME);

        when(repository.findById(recurringId)).thenReturn(Optional.of(current));
        when(repositoryWalletUser.findByWalletIdAndUserId(request.getWalletId(), userId))
                .thenReturn(Optional.of(membership));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(category));
        when(repository.save(current)).thenReturn(current);

        RecurringResponse response = service.updateRecurring(recurringId, request, userId);

        assertNull(current.getEndDate());
        assertEquals(new BigDecimal("2000.00"), current.getAmount());
        assertEquals(LocalDate.of(2026, 11, 1), current.getNextExecutionDate());
        assertFalse(current.getActive(), "PUT must not silently resume a paused salary");
        assertNull(response.getEndDate());
        verify(repository).save(current);
    }

    @Test
    void updateRecurring_scheduleChangeRecalculatesNextRunFromStartDate() {
        UUID userId = UUID.randomUUID();
        UUID recurringId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"),
                ZoneId.of("America/El_Salvador"));
        RecurringExecutionService service = serviceWithClock(clock);
        RecurringTransaction current = recurrence(userId, RecurringFrequency.MONTHLY,
                LocalDate.of(2026, 1, 1), null);
        current.setId(recurringId);
        current.setNextExecutionDate(LocalDate.of(2026, 10, 1));
        RecurringRequest request = request(LocalDate.of(2026, 9, 1), null);
        WalletUser membership = walletUser(userId);
        membership.getWallet().setId(request.getWalletId());
        Category category = new Category();
        category.setType(CategoryType.EXPENSE);
        when(repository.findById(recurringId)).thenReturn(Optional.of(current));
        when(repositoryWalletUser.findByWalletIdAndUserId(request.getWalletId(), userId))
                .thenReturn(Optional.of(membership));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(category));
        when(repository.save(current)).thenReturn(current);

        service.updateRecurring(recurringId, request, userId);

        assertEquals(LocalDate.of(2026, 11, 1), current.getNextExecutionDate());
    }

    @Test
    void updateRecurring_rejectsCategoryOfDifferentFinancialType() {
        UUID userId = UUID.randomUUID();
        UUID recurringId = UUID.randomUUID();
        RecurringExecutionService service = serviceWithClock(Clock.system(
                ZoneId.of("America/El_Salvador")));
        RecurringTransaction current = recurrence(userId, RecurringFrequency.MONTHLY,
                LocalDate.now(), null);
        current.setId(recurringId);
        RecurringRequest request = request(LocalDate.now(), null);
        Category income = new Category();
        income.setType(CategoryType.INCOME);
        WalletUser membership = walletUser(userId);
        membership.getWallet().setId(request.getWalletId());
        when(repository.findById(recurringId)).thenReturn(Optional.of(current));
        when(repositoryWalletUser.findByWalletIdAndUserId(request.getWalletId(), userId))
                .thenReturn(Optional.of(membership));
        when(repositoryCategory.findById(request.getCategoryId()))
                .thenReturn(Optional.of(income));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateRecurring(recurringId, request, userId));
        verify(repository, never()).save(any());
    }

    private RecurringExecutionService serviceWithClock(final Clock clock) {
        return new RecurringExecutionService(repository, executor,
                emailTemplateService, userService, repositoryCategory,
                repositoryWalletUser, planLimitService, messageService, clock);
    }

    private RecurringTransaction recurrence(
            final UUID userId, final RecurringFrequency frequency,
            final LocalDate startDate, final LocalDate endDate
    ) {
        User owner = new User();
        owner.setId(userId);
        RecurringTransaction recurrence = new RecurringTransaction();
        recurrence.setUser(owner);
        recurrence.setTitle("Salary");
        recurrence.setAmount(new BigDecimal("2000.00"));
        recurrence.setType(RecurringType.INCOME);
        recurrence.setFrequency(frequency);
        recurrence.setIntervalValue(1);
        recurrence.setStartDate(startDate);
        recurrence.setNextExecutionDate(startDate);
        recurrence.setEndDate(endDate);
        recurrence.setActive(true);
        return recurrence;
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
