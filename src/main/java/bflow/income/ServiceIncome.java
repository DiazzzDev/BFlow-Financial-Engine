package bflow.income;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.category.RepositoryCategory;
import bflow.category.CategoryValidator;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.common.financial.TransactionMapper;
import bflow.common.i18n.MessageService;
import bflow.income.DTO.IncomeRequest;
import bflow.income.DTO.IncomeResponse;
import bflow.income.entity.Income;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringType;
import bflow.recurring.services.RecurringLinkService;
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
     * Service for wallet locking validation.
     */
    private final WalletLockService walletLockService;

    /**
     * Service for transaction recurring creation.
     */
    private final RecurringLinkService recurringLinkService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

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
        userService.validateUserActive(userId);

        repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(), userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.accessDenied")));

        Wallet wallet = repositoryWallet.findByIdForUpdate(
                        request.getWalletId()
                )
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                messageService.get("wallet.notFound")
                        )
                );

        User contributor = repositoryUser.findById(userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("authenticatedUser.notFound"))
                );

        Income income = mapToEntity(request, wallet, contributor);
        serviceWallet.addBalance(wallet, income.getAmount());
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
        userService.validateUserActive(userId);

        Income income = repositoryIncome.findById(incomeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("income.notFound")
                ));

        UUID oldWalletId = income.getWallet().getId();
        UUID newWalletId = request.getWalletId();

        repositoryWalletUser.findByWalletIdAndUserId(oldWalletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.accessDenied")));
        repositoryWalletUser.findByWalletIdAndUserId(newWalletId, userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.target.accessDenied")));

        WalletPair wallets = walletLockService.lockWallets(
                oldWalletId,
                newWalletId
        );

        Wallet oldWallet = wallets.oldWallet();
        Wallet newWallet = wallets.newWallet();

        BigDecimal oldAmount = income.getAmount();
        BigDecimal newAmount = request.getAmount();

        Category category = repositoryCategory.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("category.notFound")
                ));

        if (category.getType() != CategoryType.INCOME) {
            throw new IllegalArgumentException(
                    messageService.get(
                            "category.invalidType.income", category.getType()
                    )
            );
        }

        if (oldWalletId.equals(newWalletId)) {
            serviceWallet.adjustBalanceForIncomeUpdate(
                    oldWallet, oldAmount, newAmount
            );
        } else {
            serviceWallet.subtractBalance(oldWallet, oldAmount);
            serviceWallet.addBalance(newWallet, newAmount);
            income.setWallet(newWallet);
        }

        boolean wasRecurring = Boolean.TRUE.equals(income.getRecurring());
        boolean willBeRecurring = Boolean.TRUE.equals(request.getRecurring());

        UUID recurringId = recurringLinkService.syncOnUpdate(
                new RecurringLinkService.RecurringSyncRequest(
                        RecurringType.INCOME,
                        wasRecurring,
                        income.getRecurringTransactionId(),
                        willBeRecurring,
                        request.getRecurrencePattern(),
                        newWallet,
                        category,
                        income.getContributor(),
                        request.getTitle(),
                        request.getDescription(),
                        newAmount,
                        request.getDate()
                )
        );

        income.setTitle(request.getTitle());
        income.setDescription(request.getDescription());
        income.setAmount(newAmount);
        income.setDate(request.getDate());
        income.setCategory(category);
        income.setTaxable(Boolean.TRUE.equals(request.getTaxable()));
        income.setRecurring(willBeRecurring);
        income.setRecurrencePattern(request.getRecurrencePattern());
        income.setRecurringTransactionId(recurringId);

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
        userService.validateUserActive(userId);

        Income income = repositoryIncome.findById(incomeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                                messageService.get("income.notFound")
                        )
                );

        repositoryWalletUser
                .findByWalletIdAndUserId(income.getWallet().getId(), userId)
                .orElseThrow(() -> new WalletAccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        Wallet wallet = repositoryWallet
                .findByIdForUpdate(income.getWallet().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        messageService.get("wallet.notFound")
                ));

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
                                messageService.get("category.notFound")
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

        boolean isAutoGenerated = "recurring".equalsIgnoreCase(
                request.getSource()
        );
        if (Boolean.TRUE.equals(income.getRecurring()) && !isAutoGenerated) {
            RecurringTransaction recurring = recurringLinkService.linkRecurring(
                    new RecurringLinkService.RecurringCreateRequest(
                            RecurringType.INCOME,
                            request.getRecurrencePattern(),
                            wallet,
                            category,
                            contributor,
                            income.getTitle(),
                            income.getDescription(),
                            income.getAmount(),
                            income.getDate()
                    )
            );
            income.setRecurringTransactionId(recurring.getId());
        }

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

        response.setRecurring(income.getRecurring());
        response.setRecurrencePattern(income.getRecurrencePattern());
        response.setRecurringTransactionId(
                income.getRecurringTransactionId() != null
                        ? income.getRecurringTransactionId().toString()
                        : null
        );

        return response;
    }

}
