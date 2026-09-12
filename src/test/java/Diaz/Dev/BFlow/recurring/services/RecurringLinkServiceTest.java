package Diaz.Dev.BFlow.recurring.services;

import bflow.auth.entities.User;
import bflow.category.entity.Category;
import bflow.common.exception.PlanLimitExceededException;
import bflow.common.i18n.MessageService;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringLinkService;
import bflow.subscription.FeatureCodes;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RecurringLinkService: the create-time link
 * (linkRecurring) and the update-time reconciliation (syncOnUpdate)
 * that back the {@code recurring=true} flag on income/expense
 * creation and updates.
 */
@ExtendWith(MockitoExtension.class)
class RecurringLinkServiceTest {

    @Mock
    private RepositoryRecurringTransaction repository;

    @Mock
    private PlanLimitService planLimitService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private RecurringLinkService recurringLinkService;

    private UUID userId;
    private User user;
    private Wallet wallet;
    private Category category;
    private LocalDate startDate;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        user = new User();
        user.setId(userId);

        wallet = new Wallet();
        wallet.setId(UUID.randomUUID());

        category = new Category();
        category.setId(UUID.randomUUID());

        startDate = LocalDate.of(2026, 8, 17);

        lenient().when(repository.save(any(RecurringTransaction.class)))
                .thenAnswer(invocation -> {
                    RecurringTransaction entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(UUID.randomUUID());
                    }
                    return entity;
                });
    }

    // ---- linkRecurring (create flow, POST) ----

    @Test
    void linkRecurring_monthly_nextExecutionDateIsStartPlusOneMonth() {
        RecurringTransaction result = recurringLinkService.linkRecurring(
                new RecurringLinkService.RecurringCreateRequest(
                        RecurringType.EXPENSE, "MONTHLY", wallet, category,
                        user, "Rent", "Apartment", BigDecimal.valueOf(500),
                        startDate));

        assertEquals(RecurringFrequency.MONTHLY, result.getFrequency());
        assertEquals(startDate, result.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 17), result.getNextExecutionDate());
        assertTrue(result.getActive());
        assertEquals(1, result.getIntervalValue());
    }

    @Test
    void linkRecurring_daily_nextExecutionDateIsStartPlusOneDay() {
        RecurringTransaction result = recurringLinkService.linkRecurring(
                new RecurringLinkService.RecurringCreateRequest(
                        RecurringType.EXPENSE, "daily", wallet, category,
                        user, "Coffee", null, BigDecimal.valueOf(3),
                        startDate));

        assertEquals(RecurringFrequency.DAILY, result.getFrequency());
        assertEquals(LocalDate.of(2026, 8, 18), result.getNextExecutionDate());
    }

    @Test
    void linkRecurring_weekly_nextExecutionDateIsStartPlusOneWeek() {
        RecurringTransaction result = recurringLinkService.linkRecurring(
                new RecurringLinkService.RecurringCreateRequest(
                        RecurringType.INCOME, "WEEKLY", wallet, category,
                        user, "Freelance retainer", null,
                        BigDecimal.valueOf(200), startDate));

        assertEquals(LocalDate.of(2026, 8, 24), result.getNextExecutionDate());
    }

    @Test
    void linkRecurring_blankPattern_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                recurringLinkService.linkRecurring(
                        new RecurringLinkService.RecurringCreateRequest(
                                RecurringType.EXPENSE, "  ", wallet,
                                category, user, "Rent", null,
                                BigDecimal.TEN, startDate)));

        verify(repository, never()).save(any());
    }

    @Test
    void linkRecurring_unsupportedPattern_throwsIllegalArgumentException() {
        // YEARLY passes BaseTransactionRequest's @Pattern regex but isn't
        // a RecurringFrequency value yet — must fail loudly, not silently.
        lenient().when(messageService.get(
                eq("recurring.recurrencePattern.unsupported"), any()
        )).thenAnswer(inv -> "Unsupported recurrencePattern '"
                + inv.getArgument(1) + "'");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () ->
                        recurringLinkService.linkRecurring(
                                new RecurringLinkService.RecurringCreateRequest(
                                        RecurringType.EXPENSE, "YEARLY",
                                        wallet, category, user,
                                        "Insurance", null,
                                        BigDecimal.TEN, startDate)));

        assertTrue(ex.getMessage().contains("YEARLY"));
        verify(repository, never()).save(any());
    }

    @Test
    void linkRecurring_enforcesPlanLimitBeforeSaving() {
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(5L);
        doThrow(new PlanLimitExceededException(
                "Recurring transaction limit reached"))
                .when(planLimitService).assertCanCreate(
                        eq(userId), eq(FeatureCodes.RECURRING_TRANSACTIONS),
                        eq(5L));

        assertThrows(PlanLimitExceededException.class, () ->
                recurringLinkService.linkRecurring(
                        new RecurringLinkService.RecurringCreateRequest(
                                RecurringType.EXPENSE, "MONTHLY", wallet,
                                category, user, "Rent", null,
                                BigDecimal.TEN, startDate)));

        verify(repository, never()).save(any());
    }

    // ---- syncOnUpdate (PUT reconciliation) ----

    @Test
    void syncOnUpdate_falseToFalse_isNoOp() {
        UUID result = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE, false, null, false, null,
                        wallet, category, user, "Title", null,
                        BigDecimal.TEN, startDate));

        assertNull(result);
        verify(repository, never()).save(any());
        verify(repository, never()).findById(any());
    }

    @Test
    void syncOnUpdate_falseToTrue_createsNewTemplate() {
        UUID result = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE, false, null, true, "MONTHLY",
                        wallet, category, user, "Gym membership", null,
                        BigDecimal.valueOf(40), startDate));

        assertNotNull(result);
        verify(repository).save(any(RecurringTransaction.class));
    }

    @Test
    void syncOnUpdate_trueToFalse_deactivatesExistingTemplate() {
        UUID existingId = UUID.randomUUID();
        RecurringTransaction existing = new RecurringTransaction();
        existing.setId(existingId);
        existing.setUser(user);
        existing.setActive(true);

        when(repository.findById(existingId))
                .thenReturn(Optional.of(existing));

        UUID result = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE, true, existingId, false, null,
                        wallet, category, user, "Gym membership", null,
                        BigDecimal.valueOf(40), startDate));

        assertNull(result);
        assertFalse(existing.getActive());
        verify(repository, never()).save(any());
    }

    @Test
    void syncOnUpdate_trueToTrue_samePattern_keepsNextExecutionDate() {
        UUID existingId = UUID.randomUUID();
        LocalDate originalNext = LocalDate.of(2026, 9, 1);

        RecurringTransaction existing = new RecurringTransaction();
        existing.setId(existingId);
        existing.setUser(user);
        existing.setActive(true);
        existing.setFrequency(RecurringFrequency.MONTHLY);
        existing.setNextExecutionDate(originalNext);
        existing.setAmount(BigDecimal.valueOf(40));

        when(repository.findById(existingId))
                .thenReturn(Optional.of(existing));

        UUID result = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE, true, existingId, true,
                        "MONTHLY", wallet, category, user,
                        "Gym membership (renamed)", null,
                        BigDecimal.valueOf(45), startDate));

        // Unrelated edits (title, amount) must not shift the schedule.
        assertEquals(existingId, result);
        assertEquals(originalNext, existing.getNextExecutionDate());
        assertEquals(BigDecimal.valueOf(45), existing.getAmount());
        assertEquals("Gym membership (renamed)", existing.getTitle());
    }

    @Test
    void syncOnUpdate_trueToTrue_patternChanged_recalculatesNextExecutionDate() {
        UUID existingId = UUID.randomUUID();

        RecurringTransaction existing = new RecurringTransaction();
        existing.setId(existingId);
        existing.setUser(user);
        existing.setActive(true);
        existing.setFrequency(RecurringFrequency.MONTHLY);
        existing.setNextExecutionDate(LocalDate.of(2026, 9, 1));
        existing.setAmount(BigDecimal.valueOf(40));

        when(repository.findById(existingId))
                .thenReturn(Optional.of(existing));

        recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE, true, existingId, true,
                        "WEEKLY", wallet, category, user,
                        "Gym membership", null,
                        BigDecimal.valueOf(40), startDate));

        assertEquals(RecurringFrequency.WEEKLY, existing.getFrequency());
        assertEquals(LocalDate.of(2026, 8, 24), existing.getNextExecutionDate());
    }

    @Test
    void syncOnUpdate_trueToTrue_linkMissing_selfHealsByCreatingNewTemplate() {
        UUID staleId = UUID.randomUUID();
        when(repository.findById(staleId)).thenReturn(Optional.empty());

        UUID result = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE, true, staleId, true,
                        "MONTHLY", wallet, category, user, "Rent", null,
                        BigDecimal.TEN, startDate));

        assertNotNull(result);
        assertNotEquals(staleId, result);
        verify(repository).save(any(RecurringTransaction.class));
    }

    @Test
    void syncOnUpdate_trueToTrue_linkBelongsToDifferentUser_selfHeals() {
        UUID existingId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        RecurringTransaction foreignTemplate = new RecurringTransaction();
        foreignTemplate.setId(existingId);
        foreignTemplate.setUser(otherUser);
        foreignTemplate.setActive(true);

        when(repository.findById(existingId))
                .thenReturn(Optional.of(foreignTemplate));

        UUID result = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE, true, existingId, true,
                        "MONTHLY", wallet, category, user, "Rent", null,
                        BigDecimal.TEN, startDate));

        assertNotNull(result);
        verify(repository).save(any(RecurringTransaction.class));
    }
}