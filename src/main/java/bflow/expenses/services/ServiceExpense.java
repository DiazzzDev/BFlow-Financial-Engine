package bflow.expenses.services;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.budget.services.BudgetService;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.category.RepositoryCategory;
import bflow.category.CategoryValidator;
import bflow.common.exception.FileAccessDeniedException;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.financial.TransactionMapper;
import bflow.common.i18n.MessageService;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringLinkService;
import bflow.storage.entity.StoredFile;
import bflow.storage.enums.FileStatus;
import bflow.storage.repository.RepositoryStoredFile;
import bflow.wallet.DTO.WalletPair;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;
import bflow.wallet.service.WalletLockService;
import bflow.wallet.entities.Wallet;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceExpense {
    /**
     * Repository for expense entity operations.
     */
    private final RepositoryExpense repositoryExpense;

    /**
     * Repository for wallet user entity operations.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Repository for user entity operations.
     */
    private final RepositoryUser repositoryUser;

    /**
     * Repository for wallet entity operations.
     */
    private final RepositoryWallet repositoryWallet;

    /**
     * Service for wallet business logic operations.
     */
    private final ServiceWallet serviceWallet;

    /**
     * Service for user business logic operations.
     */
    private final UserService userService;

    /**
     * Repository for category entity operations.
     */
    private final RepositoryCategory repositoryCategory;

    /**
     * Validator for category operations.
     */
    private final CategoryValidator categoryValidator;

    /**
     * Service for budget business logic operations.
     */
    private final BudgetService serviceBudget;

    /**
     * Service for wallet locking validation.
     */
    private final WalletLockService walletLockService;

    /**
     * Service for transaction recurring creation.
     */
    private final RecurringLinkService recurringLinkService;

    /**
     * Repository for file storage persistence.
     */
    private final RepositoryStoredFile repositoryStoredFile;

    /** Service for resolving localized messages. */
    private final MessageService messageService;


    /**
     * Creates a new expense entry for the specified wallet and user.
     *
     * @param request the expense request containing expense details
     * @param userId the unique identifier of the authenticated user
     * @return the created expense as an ExpenseResponse
     * @throws WalletAccessDeniedException if the user does not have access
     *         to the wallet
     */
    public ExpenseResponse newExpense(
            final ExpenseRequest request,
            final UUID userId
    ) {
        userService.validateUserActive(userId);

        repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(), userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.accessDenied")));

        Wallet wallet = repositoryWallet
                .findByIdForUpdate(request.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("wallet.notFound")
                ));

        User contributor = repositoryUser.findById(userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("authenticatedUser.notFound")
                ));

        StoredFile receiptFile = resolveReceiptFile(
                request.getReceiptFileId(),
                userId
        );

        Expense expense = mapToEntity(request, wallet, contributor);
        expense.setReceiptFile(receiptFile);

        serviceWallet.subtractBalance(wallet, expense.getAmount());

        Expense savedExpense = repositoryExpense.saveAndFlush(expense);

        serviceBudget.evaluateBudgetsForExpenseEvent(
                savedExpense.getWallet().getId(),
                savedExpense.getCategory().getId()
        );

        return mapToResponse(savedExpense);
    }

    /**
     * Updates an existing expense entry for the specified user.
     *
     * @param expenseId the unique identifier of the expense to update
     * @param request the expense request containing updated details
     * @param userId the unique identifier of the authenticated user
     * @return the updated expense as an ExpenseResponse
     * @throws ResourceNotFoundException if the expense is not found
     * @throws WalletAccessDeniedException if the user lacks access to
     *         the wallets
     */
    public ExpenseResponse updateExpense(
            final UUID expenseId,
            final ExpenseRequest request,
            final UUID userId
    ) {
        userService.validateUserActive(userId);

        Expense expense = repositoryExpense.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("expense.notFound")
                ));

        UUID oldWalletId = expense.getWallet().getId();
        UUID newWalletId = request.getWalletId();

        repositoryWalletUser.findByWalletIdAndUserId(oldWalletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));
        repositoryWalletUser.findByWalletIdAndUserId(newWalletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.target.accessDenied")
                ));

        WalletPair wallets = walletLockService.lockWallets(
                oldWalletId,
                newWalletId
        );

        Wallet oldWallet = wallets.oldWallet();
        Wallet newWallet = wallets.newWallet();

        BigDecimal oldAmount = expense.getAmount();
        BigDecimal newAmount = request.getAmount();

        Category category = repositoryCategory.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("category.notFound")
                ));

        if (category.getType() != CategoryType.EXPENSE) {
            throw new IllegalArgumentException(
                    messageService.get(
                            "category.invalidType.expense", category.getType()
                    )
            );
        }

        if (oldWalletId.equals(newWalletId)) {
            serviceWallet.adjustBalanceForExpenseUpdate(
                    oldWallet, oldAmount, newAmount
            );
        } else {
            serviceWallet.reverseTransactionImpact(oldWallet, oldAmount);
            serviceWallet.subtractBalance(newWallet, newAmount);
            expense.setWallet(newWallet);
        }

        boolean wasRecurring = Boolean.TRUE.equals(expense.getRecurring());
        boolean willBeRecurring = Boolean.TRUE.equals(request.getRecurring());

        UUID recurringId = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.EXPENSE,
                        wasRecurring,
                        expense.getRecurringTransactionId(),
                        willBeRecurring,
                        request.getRecurrencePattern(),
                        newWallet,
                        category,
                        expense.getContributor(),
                        request.getTitle(),
                        request.getDescription(),
                        newAmount,
                        request.getDate()
                )
        );

        UUID oldCategoryId = expense.getCategory() != null
                ? expense.getCategory().getId()
                : null;

        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setAmount(newAmount);
        expense.setDate(request.getDate());
        expense.setCategory(category);
        expense.setTaxDeductible(
                Boolean.TRUE.equals(request.getTaxDeductible())
        );
        expense.setRecurring(willBeRecurring);
        expense.setRecurrencePattern(request.getRecurrencePattern());
        expense.setRecurringTransactionId(recurringId);

        repositoryExpense.save(expense);

        serviceBudget.evaluateBudgetsForExpenseEvent(
                newWallet.getId(),
                category.getId()
        );

        if (!oldWalletId.equals(newWalletId)) {
            serviceBudget.evaluateBudgetsForExpenseEvent(
                    oldWallet.getId(),
                    oldCategoryId
            );
        }

        return mapToResponse(expense);
    }

    /**
     * Deletes an expense entry for the specified user.
     *
     * @param expenseId the unique identifier of the expense to delete
     * @param userId the unique identifier of the authenticated user
     * @throws ResourceNotFoundException if the expense is not found
     * @throws WalletAccessDeniedException if the user does not have access
     *         to the wallet
     */
    public void deleteExpense(
            final UUID expenseId,
            final UUID userId
    ) {
        userService.validateUserActive(userId);

        Expense expense = repositoryExpense.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("expense.notFound")
                ));

        repositoryWalletUser.
                findByWalletIdAndUserId(expense.getWallet().getId(), userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        Wallet wallet = repositoryWallet
                .findByIdForUpdate(expense.getWallet().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("wallet.notFound")
                ));

        serviceWallet.addBalance(wallet, expense.getAmount());

        repositoryExpense.delete(expense);

        serviceBudget.evaluateBudgetsForExpenseEvent(
            wallet.getId(),
            expense.getCategory() != null ? expense.getCategory().getId() : null
        );
    }

    /**
     * Maps an ExpenseRequest DTO to an Expense entity.
     *
     * @param request the expense request containing expense details
     * @param wallet the wallet to associate with the expense
     * @param contributor the user contributing the expense
     * @return the mapped Expense entity
     */
    private Expense mapToEntity(
            final ExpenseRequest request,
            final Wallet wallet,
            final User contributor
    ) {
        // Resolve and validate category
        Category category = repositoryCategory
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                messageService.get("category.notFound")
                        )
                );

        categoryValidator.validateExpenseCategory(category);

        Expense expense = new Expense();

        // ---- FinancialEntry fields ----
        expense.setTitle(request.getTitle().trim());
        expense.setDescription(request.getDescription());
        expense.setAmount(
                request.getAmount().setScale(2, RoundingMode.HALF_EVEN)
        );
        expense.setDate(request.getDate());
        expense.setWallet(wallet);
        expense.setContributor(contributor);
        expense.setCategory(category);
        expense.setSource(request.getSource());
        expense.setAutoGenerated(
                "recurring".equalsIgnoreCase(request.getSource())
        );

        // ---- Expense specific fields ----
        expense.setTaxDeductible(
                Boolean.TRUE.equals(request.getTaxDeductible())
        );
        expense.setRecurring(
                Boolean.TRUE.equals(request.getRecurring())
        );
        expense.setRecurrencePattern(request.getRecurrencePattern());
        expense.setReimbursable(
                Boolean.TRUE.equals(request.getReimbursable())
        );

        boolean isAutoGenerated = "recurring".equalsIgnoreCase(
                request.getSource()
        );
        if (Boolean.TRUE.equals(expense.getRecurring()) && !isAutoGenerated) {
            RecurringTransaction recurring = recurringLinkService.linkRecurring(
                    new RecurringLinkService.RecurringCreateRequest(
                            RecurringType.EXPENSE,
                            request.getRecurrencePattern(),
                            wallet,
                            category,
                            contributor,
                            expense.getTitle(),
                            expense.getDescription(),
                            expense.getAmount(),
                            expense.getDate()
                    )
            );
            expense.setRecurringTransactionId(recurring.getId());
        }

        return expense;
    }

    /**
     * Maps an Expense entity to an ExpenseResponse DTO.
     *
     * @param expense the expense entity to map
     * @return the mapped ExpenseResponse
     */
    public ExpenseResponse mapToResponse(final Expense expense) {

        ExpenseResponse response = new ExpenseResponse();

        response.setId(expense.getId().toString());
        response.setTitle(expense.getTitle());
        response.setDescription(expense.getDescription());
        response.setAmount(expense.getAmount());
        response.setDate(expense.getDate());
        response.setCategory(
                TransactionMapper.mapCategoryToResponse(expense.getCategory())
        );

        response.setTaxDeductible(expense.getTaxDeductible());
        response.setRecurring(expense.getRecurring());
        response.setReimbursable(expense.getReimbursable());

        response.setWalletId(expense.getWallet().getId().toString());
        response.setWalletName(expense.getWallet().getName());

        response.setContributorId(
                expense.getContributor().getId().toString()
        );
        response.setContributorName(
                expense.getContributor().getEmail()
        );

        response.setSource(expense.getSource());
        response.setConfidenceScore(expense.getConfidenceScore());
        response.setCreatedAt(expense.getCreatedAt());
        response.setCategorizationChanges(expense.getCategorizationChanges());
        response.setEditCount(expense.getEditCount());

        response.setRecurrencePattern(expense.getRecurrencePattern());
        response.setRecurringTransactionId(
                expense.getRecurringTransactionId() != null
                        ? expense.getRecurringTransactionId().toString()
                        : null
        );

        response.setReceiptFileId(
                expense.getReceiptFile() != null
                        ? expense.getReceiptFile().getId().toString()
                        : null
        );

        return response;
    }

    private StoredFile resolveReceiptFile(
            final UUID receiptFileId,
            final UUID userId
    ) {
        if (receiptFileId == null) {
            return null;
        }

        StoredFile receipt = repositoryStoredFile
                .findByIdAndUserId(receiptFileId, userId)
                .orElseThrow(() -> new FileAccessDeniedException(
                        messageService.get("receipt.accessDenied")
                ));

        if (receipt.getStatus() != FileStatus.UPLOADED) {
            throw new IllegalStateException(
                    messageService.get("receipt.notReady")
            );
        }

        return receipt;
    }
}
