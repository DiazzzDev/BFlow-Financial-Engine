package bflow.recurring.services;

import bflow.auth.services.UserService;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.i18n.MessageService;
import bflow.recurring.DTO.RecurringRequest;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.subscription.FeatureCodes;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing recurring transactions (CRUD + scheduling
 * orchestration). Actual execution of individual transactions and
 * failure handling is delegated to {@link RecurringTransactionExecutor}
 * so each one runs in its own isolated transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecurringExecutionService {

    /**
     * Repository for recurring transaction persistence.
     */
    private final RepositoryRecurringTransaction repository;

    /**
     * Executor responsible for running each recurring transaction in
     * isolation and recording failures.
     */
    private final RecurringTransactionExecutor executor;

    /**
     * Service used to send failure notification emails.
     */
    private final EmailTemplateService emailTemplateService;

    /**
     * Service for user validation.
     */
    private final UserService userService;

    /**
     * Repository for category persistence.
     */
    private final RepositoryCategory repositoryCategory;

    /**
     * Repository for wallet user associations.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Service responsible for enforcing subscription plan
     * limits and feature availability.
     */
    private final PlanLimitService planLimitService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Execute all due recurring transactions on the current date.
     * Each one runs in its own isolated transaction (delegated to
     * {@link RecurringTransactionExecutor}); a failure is recorded and
     * notified, never blocking the rest of the batch.
     */
    public void executeDueTransactions() {
        List<RecurringTransaction> due =
                repository.findDueTransactions(LocalDate.now());

        for (RecurringTransaction recurring : due) {
            UUID id = recurring.getId();
            try {
                executor.executeSingle(id);
            } catch (Exception e) {
                log.error("Failed to execute recurring transaction {}: {}",
                        id, e.getMessage());
                notifyFailure(id, e);
            }
        }
    }

    /**
     * Records the failure and sends a notification email, isolating
     * each step so an email/SES issue never affects data integrity.
     *
     * @param id the recurring transaction ID
     * @param error the exception that caused the failure
     */
    private void notifyFailure(final UUID id, final Exception error) {
        RecurringTransactionExecutor.FailureNotification notification;
        try {
            notification = executor.recordFailure(id, error);
        } catch (Exception recordError) {
            log.error("Failed to record failure for recurring {}: {}",
                    id, recordError.getMessage());
            return;
        }

        if (notification == null) {
            return;
        }

        try {
            emailTemplateService.sendRecurringFailedEmail(
                    notification.email(),
                    notification.userName(),
                    notification.transactionTitle(),
                    notification.amount(),
                    notification.attempts(),
                    notification.deactivated(),
                    notification.reason(),
                    notification.language()
            );
        } catch (Exception mailError) {
            log.error("Failed to send recurring-failure email for {}: {}",
                    id, mailError.getMessage());
        }
    }

    /**
     * Get all recurring transactions for a user.
     *
     * @param userId the user ID
     * @return list of recurring transaction responses
     */
    public List<RecurringResponse> getUserRecurring(
            final UUID userId
    ) {
        return repository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Create a new recurring transaction.
     *
     * @param request the recurring transaction request
     * @param userId the user ID
     * @return the created recurring transaction response
     */
    public RecurringResponse createRecurring(
            final RecurringRequest request,
            final UUID userId
    ) {
        userService.validateUserActive(userId);

        planLimitService.assertCanCreate(
                userId, FeatureCodes.RECURRING_TRANSACTIONS,
                repository.countByUserIdAndActiveTrue(userId));

        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(), userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                messageService.get("wallet.accessDenied")
                        )
                );

        Wallet wallet = walletUser.getWallet();

        Category category = repositoryCategory
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                messageService.get("category.notFound")
                        )
                );

        RecurringTransaction recurring = new RecurringTransaction();

        recurring.setTitle(request.getTitle());
        recurring.setDescription(request.getDescription());
        recurring.setAmount(request.getAmount());

        recurring.setWallet(wallet);
        recurring.setCategory(category);
        recurring.setUser(walletUser.getUser());

        recurring.setType(request.getType());
        recurring.setFrequency(request.getFrequency());
        recurring.setIntervalValue(request.getIntervalValue());

        recurring.setStartDate(request.getStartDate());
        recurring.setNextExecutionDate(request.getStartDate());

        recurring.setEndDate(request.getEndDate());
        recurring.setActive(true);

        RecurringTransaction saved = repository.save(recurring);

        return mapToResponse(saved);
    }

    /**
     * Map recurring transaction entity to response DTO.
     *
     * @param req the recurring transaction entity
     * @return the recurring transaction response
     */
    private RecurringResponse mapToResponse(final RecurringTransaction req) {
        RecurringResponse res = new RecurringResponse();
        res.setId(req.getId());
        res.setTitle(req.getTitle());
        res.setAmount(req.getAmount());
        res.setType(req.getType());
        res.setFrequency(req.getFrequency());
        res.setIntervalValue(req.getIntervalValue());
        res.setNextExecutionDate(req.getNextExecutionDate());
        res.setActive(req.getActive());
        res.setWalletId(req.getWallet().getId());
        res.setCategoryId(req.getCategory().getId());
        return res;
    }

    /**
     * Toggle the active status of a recurring transaction.
     *
     * @param id the recurring transaction ID
     * @param userId the user ID
     * @param active the new active status
     */
    public void toggleRecurring(
            final UUID id,
            final UUID userId,
            final boolean active
    ) {
        RecurringTransaction recurring = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                messageService.get("recurring.notFound")
                        )
                );

        if (!recurring.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException(
                    messageService.get("wallet.accessDenied")
            );
        }

        recurring.setActive(active);
    }

    /**
     * Delete a recurring transaction.
     *
     * @param id the recurring transaction ID
     * @param userId the user ID
     */
    public void deleteRecurring(
            final UUID id,
            final UUID userId
    ) {
        RecurringTransaction recurring = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                messageService.get("recurring.notFound")
                        )
                );

        if (!recurring.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException(
                    messageService.get("wallet.accessDenied")
            );
        }

        repository.delete(recurring);
    }
}
