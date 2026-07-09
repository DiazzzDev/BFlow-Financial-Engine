package bflow.expenses.services;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.budget.services.BudgetService;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.category.RepositoryCategory;
import bflow.category.CategoryValidator;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.financial.TransactionMapper;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.wallet.DTO.WalletPair;
import bflow.wallet.RepositoryWallet;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.ServiceWallet;
import bflow.wallet.WalletLockService;
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
    private final UserServiceImpl userService;

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
                        "You do not have access to this wallet"));

        Wallet wallet = repositoryWallet
                .findByIdForUpdate(request.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found"
                ));

        User contributor = repositoryUser.findById(userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        "Authenticated user not found"
                ));

        Expense expense = mapToEntity(request, wallet, contributor);
        serviceWallet.subtractBalance(wallet, expense.getAmount());

        Expense savedExpense = repositoryExpense.saveAndFlush(expense);
        serviceBudget.evaluateBudgetsForWallet(
                savedExpense.getWallet().getId()
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
                        "Expense not found"
                ));

        UUID oldWalletId = expense.getWallet().getId();
        UUID newWalletId = request.getWalletId();

        repositoryWalletUser.findByWalletIdAndUserId(oldWalletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        "You do not have access to this wallet"
                ));
        repositoryWalletUser.findByWalletIdAndUserId(newWalletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        "You do not have access to the target wallet"
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
                        "Category not found"
                ));

        if (category.getType() != CategoryType.EXPENSE) {
            throw new IllegalArgumentException(
                    "Category must be of type EXPENSE"
            );
        }

        if (oldWalletId.equals(newWalletId)) {
            serviceWallet.adjustBalanceForUpdate(
                oldWallet, oldAmount, newAmount
        );
        } else {
            serviceWallet.reverseTransactionImpact(oldWallet, oldAmount);
            serviceWallet.subtractBalance(newWallet, newAmount);
            expense.setWallet(newWallet);
        }

        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setAmount(newAmount);
        expense.setDate(request.getDate());
        expense.setCategory(category);
        expense.setTaxDeductible(Boolean.TRUE.equals(
                request.getTaxDeductible())
        );
        expense.setRecurring(Boolean.TRUE.equals(request.getRecurring()));
        expense.setRecurrencePattern(request.getRecurrencePattern());

        repositoryExpense.save(expense);

        serviceBudget.evaluateBudgetsForWallet(newWallet.getId());
        if (!oldWalletId.equals(newWalletId)) {
            serviceBudget.evaluateBudgetsForWallet(oldWallet.getId());
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
                        "Expense not found"
                ));

        repositoryWalletUser.
                findByWalletIdAndUserId(expense.getWallet().getId(), userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        "You do not have access to this wallet"
                ));

        Wallet wallet = repositoryWallet
                .findByIdForUpdate(expense.getWallet().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found"
                ));

        serviceWallet.addBalance(wallet, expense.getAmount());
        repositoryExpense.delete(expense);
        serviceBudget.evaluateBudgetsForWallet(wallet.getId());
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
                                "Category not found"
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

        return response;
    }

}
