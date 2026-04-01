package bflow.recurring.services;

import bflow.auth.services.UserServiceImpl;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.ServiceExpense;
import bflow.income.DTO.IncomeRequest;
import bflow.income.ServiceIncome;
import bflow.recurring.DTO.RecurringRequest;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringType;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for executing and managing recurring transactions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecurringExecutionService {
    /**
     * Repository for recurring transaction persistence.
     */
    private final RepositoryRecurringTransaction repository;
    /**
     * Service for expense operations.
     */
    private final ServiceExpense serviceExpense;
    /**
     * Service for income operations.
     */
    private final ServiceIncome serviceIncome;
    /**
     * Service for user validation.
     */
    private final UserServiceImpl userService;
    /**
     * Repository for category persistence.
     */
    private final RepositoryCategory repositoryCategory;
    /**
     * Repository for wallet user associations.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Execute all due recurring transactions on the current date.
     */
    public void executeDueTransactions() {
        List<RecurringTransaction> due =
                repository.findDueTransactions(LocalDate.now());

        for (RecurringTransaction recurring : due) {
            if (recurring.getType() == RecurringType.EXPENSE) {
                createExpense(recurring);
            } else {
                createIncome(recurring);
            }

            updateNextExecution(recurring);
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

        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(), userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "No access to wallet"
                        )
                );

        Wallet wallet = walletUser.getWallet();

        Category category = repositoryCategory
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category not found")
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
     * Create an expense from a recurring transaction.
     *
     * @param recurring the recurring transaction
     */
    private void createExpense(final RecurringTransaction recurring) {
        ExpenseRequest request = new ExpenseRequest();

        request.setTitle(recurring.getTitle());
        request.setDescription(recurring.getDescription());
        request.setAmount(recurring.getAmount());
        request.setDate(LocalDate.now());
        request.setWalletId(recurring.getWallet().getId());
        request.setCategoryId(recurring.getCategory().getId());
        request.setSource("recurring");
        request.setRecurring(true);

        serviceExpense.newExpense(
                request,
                recurring.getUser().getId()
        );
    }

    /**
     * Create an income from a recurring transaction.
     *
     * @param recurring the recurring transaction
     */
    private void createIncome(final RecurringTransaction recurring) {
        IncomeRequest request = new IncomeRequest();

        request.setTitle(recurring.getTitle());
        request.setDescription(recurring.getDescription());
        request.setAmount(recurring.getAmount());
        request.setDate(LocalDate.now());
        request.setWalletId(recurring.getWallet().getId());
        request.setCategoryId(recurring.getCategory().getId());
        request.setSource("recurring");
        request.setRecurring(true);

        serviceIncome.newIncome(
                request,
                recurring.getUser().getId()
        );
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
                        new ResourceNotFoundException("Recurring not found")
                );

        if (!recurring.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException("Access denied");
        }

        recurring.setActive(active);
    }

    /**
     * Update the next execution date for a recurring transaction.
     *
     * @param recurring the recurring transaction
     */
    private void updateNextExecution(
            final RecurringTransaction recurring
    ) {
        LocalDate next = recurring.getNextExecutionDate();
        LocalDate nextDate = next;

        switch (recurring.getFrequency()) {
            case DAILY:
                nextDate = next.plusDays(recurring.getIntervalValue());
                break;
            case WEEKLY:
                nextDate = next.plusWeeks(recurring.getIntervalValue());
                break;
            case MONTHLY:
                nextDate = next.plusMonths(recurring.getIntervalValue());
                break;
            default:
                break;
        }

        recurring.setNextExecutionDate(nextDate);
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
                        new ResourceNotFoundException("Recurring not found")
                );

        if (!recurring.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException("Access denied");
        }

        repository.delete(recurring);
    }
}
