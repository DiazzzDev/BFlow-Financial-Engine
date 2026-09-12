package bflow.recurring.services;

import bflow.auth.entities.User;
import bflow.category.entity.Category;
import bflow.common.i18n.MessageService;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringFrequency;
import bflow.recurring.enums.RecurringType;
import bflow.subscription.FeatureCodes;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecurringLinkService {

    /**
     * Default interval between recurring transaction executions.
     */
    private static final int DEFAULT_INTERVAL = 1;

    /**
     * Repository used to persist recurring transactions.
     */
    private final RepositoryRecurringTransaction repository;

    /**
     * Service responsible for validating recurring transaction plan limits.
     */
    private final PlanLimitService planLimitService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Request payload for a new recurring transaction.
     *
     * @param type the transaction type
     * @param rawFrequency the recurrence pattern
     * @param wallet the wallet for the recurring transaction
     * @param category the category for the recurring transaction
     * @param user the user who owns the recurring transaction
     * @param title the transaction title
     * @param description the transaction description
     * @param amount the recurring amount
     * @param startDate the first date for the recurrence
     */
    public record RecurringCreateRequest(
            RecurringType type,
            String rawFrequency,
            Wallet wallet,
            Category category,
            User user,
            String title,
            String description,
            BigDecimal amount,
            LocalDate startDate
    ) {
    }

    /**
     * Request payload for a recurring update reconciliation.
     *
     * @param type the transaction type
     * @param wasRecurring whether the transaction was previously recurring
     * @param existingRecurringTransactionId the current recurring link id
     * @param willBeRecurring whether the transaction should be recurring
     * @param rawFrequency the recurrence pattern
     * @param wallet the target wallet
     * @param category the target category
     * @param user the authenticated user
     * @param title the updated title
     * @param description the updated description
     * @param amount the updated amount
     * @param date the updated transaction date
     */
    public record RecurringSyncRequest(
            RecurringType type,
            boolean wasRecurring,
            UUID existingRecurringTransactionId,
            boolean willBeRecurring,
            String rawFrequency,
            Wallet wallet,
            Category category,
            User user,
            String title,
            String description,
            BigDecimal amount,
            LocalDate date
    ) {
    }

    /**
     * Creates a new recurring template.
     *
     * @param request the recurring creation payload
     * @return the newly created recurring transaction
     */
    public RecurringTransaction linkRecurring(
            final RecurringCreateRequest request
    ) {
        return createNew(request);
    }

    /**
     * Reconciles the recurring template against an income/expense update.
     * Only mutates the template when the update actually affects it.
     *
     * @param request the recurring sync payload
     * @return the recurring transaction id, or {@code null}
     */
    public UUID syncOnUpdate(
            final RecurringSyncRequest request
    ) {
        if (!request.wasRecurring() && !request.willBeRecurring()) {
            return null;
        }

        if (!request.wasRecurring() && request.willBeRecurring()) {
            RecurringTransaction created = createNew(new RecurringCreateRequest(
                    request.type(),
                    request.rawFrequency(),
                    request.wallet(),
                    request.category(),
                    request.user(),
                    request.title(),
                    request.description(),
                    request.amount(),
                    request.date()
            ));
            return created.getId();
        }

        if (request.wasRecurring() && !request.willBeRecurring()) {
            deactivateIfPresent(request.existingRecurringTransactionId(),
                    request.user());
            return null;
        }

        RecurringTransaction recurring =
                request.existingRecurringTransactionId() != null
                        ? repository.findById(
                                request.existingRecurringTransactionId())
                                .filter(r -> r.getUser().getId()
                                        .equals(request.user().getId()))
                                .orElse(null)
                        : null;

        if (recurring == null) {
            log.warn("Recurring flag set on a transaction with no valid "
                            + "link (user {}); creating a new template.",
                    request.user().getId());
            RecurringTransaction created = createNew(new RecurringCreateRequest(
                    request.type(),
                    request.rawFrequency(),
                    request.wallet(),
                    request.category(),
                    request.user(),
                    request.title(),
                    request.description(),
                    request.amount(),
                    request.date()
            ));
            return created.getId();
        }

        recurring.setTitle(request.title());
        recurring.setDescription(request.description());
        recurring.setAmount(request.amount());
        recurring.setWallet(request.wallet());
        recurring.setCategory(request.category());

        RecurringFrequency newFrequency =
                parseFrequency(request.rawFrequency());
        if (recurring.getFrequency() != newFrequency) {
            recurring.setFrequency(newFrequency);
            recurring.setNextExecutionDate(nextDateAfter(request.date(),
                    newFrequency));
        }

        return recurring.getId();
    }

    private RecurringTransaction createNew(
            final RecurringCreateRequest request
    ) {
        RecurringFrequency frequency = parseFrequency(request.rawFrequency());

        planLimitService.assertCanCreate(
                request.user().getId(),
                FeatureCodes.RECURRING_TRANSACTIONS,
                repository.countByUserIdAndActiveTrue(
                        request.user().getId()
                )
        );

        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setType(request.type());
        recurring.setTitle(request.title());
        recurring.setDescription(request.description());
        recurring.setAmount(request.amount());
        recurring.setFrequency(frequency);
        recurring.setIntervalValue(DEFAULT_INTERVAL);
        recurring.setStartDate(request.startDate());
        recurring.setNextExecutionDate(nextDateAfter(request.startDate(),
                frequency));
        recurring.setWallet(request.wallet());
        recurring.setCategory(request.category());
        recurring.setUser(request.user());
        recurring.setActive(true);

        return repository.save(recurring);
    }

    private void deactivateIfPresent(final UUID recurringId, final User user) {
        if (recurringId == null) {
            return;
        }
        repository.findById(recurringId)
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .ifPresent(r -> r.setActive(false));
    }

    private RecurringFrequency parseFrequency(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    messageService.get(
                            "recurring.recurrencePattern.required"
                    ));
        }
        try {
            return RecurringFrequency.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    messageService.get(
                            "recurring.recurrencePattern.unsupported", raw
                    ));
        }
    }

    private LocalDate nextDateAfter(
            final LocalDate start, final RecurringFrequency frequency
    ) {
        return switch (frequency) {
            case DAILY -> start.plusDays(DEFAULT_INTERVAL);
            case WEEKLY -> start.plusWeeks(DEFAULT_INTERVAL);
            case MONTHLY -> start.plusMonths(DEFAULT_INTERVAL);
        };
    }
}
