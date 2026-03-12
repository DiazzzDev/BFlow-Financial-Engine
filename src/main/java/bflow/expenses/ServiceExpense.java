package bflow.expenses;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.entity.Expense;
import bflow.wallet.RepositoryWallet;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.ServiceWallet;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import jakarta.transaction.Transactional;
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
        //Check if user has an active account
        userService.validateUserActive(userId);

        // Validate wallet access
        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(), userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );

        Wallet wallet = walletUser.getWallet();

        // Get contributor
        User contributor = repositoryUser.findById(userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "Authenticated user not found"
                        )
                );

        // Map DTO → Entity
        Expense expense = mapToEntity(request, wallet, contributor);

        // Subtract expense from wallet balance using service method
        serviceWallet.subtractBalance(wallet, expense.getAmount());

        Expense savedExpense = repositoryExpense.save(expense);

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
        //Check if user has an active account
        userService.validateUserActive(userId);

        Expense expense = repositoryExpense.findById(expenseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Expense not found")
                );

        Wallet oldWallet = expense.getWallet();

        // Validate access
        repositoryWalletUser
                .findByWalletIdAndUserId(
                        oldWallet.getId(),
                        userId
                )
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );

        Wallet newWallet = repositoryWallet.findById(request.getWalletId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Target wallet not found")
                );

        repositoryWalletUser
                .findByWalletIdAndUserId(
                        newWallet.getId(),
                        userId
                )
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to the target wallet"
                        )
                );

        BigDecimal oldAmount = expense.getAmount();
        BigDecimal newAmount = request.getAmount();

        if (oldWallet.getId().equals(newWallet.getId())) {

            // Same wallet → apply difference using service method
            serviceWallet.adjustBalanceForUpdate(
                    oldWallet,
                    oldAmount,
                    newAmount
            );

        } else {

            // Different wallet → full transfer logic using service methods

            // Remove old impact
            serviceWallet.reverseTransactionImpact(oldWallet, oldAmount);

            // Apply new impact
            serviceWallet.subtractBalance(newWallet, newAmount);

            // Reassign wallet
            expense.setWallet(newWallet);
        }

        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setAmount(newAmount);
        expense.setDate(request.getDate());
        expense.setType(request.getType());
        expense.setTaxDeductible(
                Boolean.TRUE.equals(request.getTaxDeductible())
        );
        expense.setRecurring(Boolean.TRUE.equals(request.getRecurring()));
        expense.setRecurrencePattern(request.getRecurrencePattern());

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
        //Check if user has an active account
        userService.validateUserActive(userId);

        Expense expense = repositoryExpense.findById(expenseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Expense not found")
                );

        // Validate access
        repositoryWalletUser
                .findByWalletIdAndUserId(
                        expense.getWallet().getId(),
                        userId
                )
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );

        Wallet wallet = expense.getWallet();

        // Reverse the expense impact on wallet balance using service method
        serviceWallet.addBalance(wallet, expense.getAmount());

        repositoryExpense.delete(expense);
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

        // ---- Expense specific fields ----
        expense.setType(request.getType());
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

        response.setExpenseType(expense.getType().name());

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

        response.setCreatedAt(expense.getCreatedAt());

        return response;
    }
}
