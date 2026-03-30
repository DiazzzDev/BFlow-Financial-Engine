package bflow.income;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserServiceImpl;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.category.RepositoryCategory;
import bflow.category.CategoryValidator;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.financial.TransactionMapper;
import bflow.income.DTO.IncomeRequest;
import bflow.income.DTO.IncomeResponse;
import bflow.income.entity.Income;
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

/**
 * Service for managing income operations within wallets.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ServiceIncome {

    /**
     * Repository for income entity operations.
     */
    private final RepositoryIncome repositoryIncome;

    /**
     * Repository for wallet user entity operations.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Repository for wallet entity operations.
     */
    private final RepositoryWallet repositoryWallet;

    /**
     * Repository for user entity operations.
     */
    private final RepositoryUser repositoryUser;

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
     * Creates a new income entry for the specified wallet and user.
     *
     * @param request the income request containing income details
     * @param userId the unique identifier of the authenticated user
     * @return the created income as an IncomeResponse
     * @throws WalletAccessDeniedException if the user does not have access to
     *         the wallet
     */
    public IncomeResponse newIncome(
            final IncomeRequest request,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        // Validate wallet access (supports shared wallets)
        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(),
                        userId)
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

        // Map dto to entity
        Income income = mapToEntity(request, wallet, contributor);

        // Add income to wallet using service method
        serviceWallet.addBalance(wallet, income.getAmount());

        // Save income into database
        Income savedIncome = repositoryIncome.saveAndFlush(income);

        return mapToResponse(savedIncome);
    }

    /**
     * Updates an existing income entry for the specified user.
     *
     * @param incomeId the unique identifier of the income to update
     * @param request the income request containing updated income details
     * @param userId the unique identifier of the authenticated user
     * @return the updated income as an IncomeResponse
     * @throws ResourceNotFoundException if the income is not found
     * @throws WalletAccessDeniedException if the user lacks access to
     *         the wallets
     */
    public IncomeResponse updateIncome(
            final UUID incomeId,
            final IncomeRequest request,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        Income income = repositoryIncome.findById(incomeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Income not found")
                );

        Wallet oldWallet = income.getWallet();

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

        BigDecimal oldAmount = income.getAmount();
        BigDecimal newAmount = request.getAmount();

        // Resolve and validate category update
        Category category = repositoryCategory
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found"
                        )
                );

        if (category.getType() != CategoryType.INCOME) {
            throw new IllegalArgumentException(
                    "Category must be of type INCOME"
            );
        }

        if (oldWallet.getId().equals(newWallet.getId())) {

            // Same wallet → apply difference using service method
            serviceWallet.adjustBalanceForUpdate(
                    oldWallet,
                    oldAmount,
                    newAmount
            );

        } else {

            // Different wallet → full transfer logic using service methods

            // Remove old balance
            serviceWallet.subtractBalance(oldWallet, oldAmount);

            // Apply new impact
            serviceWallet.addBalance(newWallet, newAmount);

            // Reassign wallet
            income.setWallet(newWallet);
        }

        income.setTitle(request.getTitle());
        income.setDescription(request.getDescription());
        income.setAmount(newAmount);
        income.setDate(request.getDate());
        income.setCategory(category);
        income.setTaxable(Boolean.TRUE.equals(request.getTaxable()));
        income.setRecurring(Boolean.TRUE.equals(request.getRecurring()));
        income.setRecurrencePattern(request.getRecurrencePattern());


        return mapToResponse(income);
    }

    /**
     * Deletes an income entry for the specified user.
     *
     * @param incomeId the unique identifier of the income to delete
     * @param userId the unique identifier of the authenticated user
     * @throws ResourceNotFoundException if the income is not found
     * @throws WalletAccessDeniedException if the user does not have access
     *         to the wallet
     */
    public void deleteIncome(
            final UUID incomeId,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        Income income = repositoryIncome.findById(incomeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Income not found")
                );

        // Validate access
        repositoryWalletUser
                .findByWalletIdAndUserId(
                        income.getWallet().getId(),
                        userId
                )
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "You do not have access to this wallet"
                        )
                );

        Wallet wallet = income.getWallet();

        // Reverse the income impact on wallet balance using service method
        serviceWallet.subtractBalance(wallet, income.getAmount());

        repositoryIncome.delete(income);
    }

    /**
     * Maps an IncomeRequest DTO to an Income entity.
     *
     * @param request the income request containing income details
     * @param wallet the wallet to associate with the income
     * @param contributor the user contributing the income
     * @return the mapped Income entity
     */
    private Income mapToEntity(
            final IncomeRequest request,
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

        categoryValidator.validateIncomeCategory(category);

        Income income = new Income();

        income.setTitle(request.getTitle().trim());
        income.setDescription(request.getDescription());

        income.setAmount(
                request.getAmount().setScale(2, RoundingMode.HALF_EVEN)
        );

        income.setDate(request.getDate());
        income.setWallet(wallet);
        income.setContributor(contributor);
        income.setCategory(category);
        income.setSource(request.getSource());
        income.setAutoGenerated(
                "recurring".equalsIgnoreCase(request.getSource())
        );

        income.setTaxable(Boolean.TRUE.equals(request.getTaxable()));
        income.setRecurring(Boolean.TRUE.equals(request.getRecurring()));
        income.setRecurrencePattern(request.getRecurrencePattern());

        return income;
    }

    /**
     * Maps an Income entity to an IncomeResponse DTO.
     *
     * @param income the income entity to map
     * @return the mapped IncomeResponse
     */
    private IncomeResponse mapToResponse(final Income income) {

        IncomeResponse response = new IncomeResponse();

        response.setId(income.getId().toString());
        response.setTitle(income.getTitle());
        response.setDescription(income.getDescription());
        response.setAmount(income.getAmount());
        response.setDate(income.getDate());
        response.setCategory(
            TransactionMapper.mapCategoryToResponse(income.getCategory())
        );

        response.setWalletId(income.getWallet().getId().toString());
        response.setWalletName(income.getWallet().getName());

        response.setContributorId(income.getContributor().getId()
                .toString());

        response.setContributorName(
                income.getContributor().getEmail()
        );

        response.setSource(income.getSource());
        response.setConfidenceScore(income.getConfidenceScore());
        response.setCreatedAt(income.getCreatedAt());
        response.setCategorizationChanges(income.getCategorizationChanges());
        response.setEditCount(income.getEditCount());

        return response;
    }

}
