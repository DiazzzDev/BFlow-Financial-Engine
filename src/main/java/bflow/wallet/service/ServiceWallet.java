package bflow.wallet.service;

import bflow.auth.services.UserService;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.DTO.IncomeResponse;
import bflow.income.RepositoryIncome;
import bflow.income.entity.Income;
import bflow.common.financial.TransactionMapper;
import bflow.common.i18n.MessageService;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.subscription.FeatureCodes;
import bflow.subscription.services.PlanLimitService;
import bflow.tranfers.RepositoryTransfers;
import bflow.wallet.DTO.UpdateWalletRequest;
import bflow.wallet.DTO.UpcomingTransactionResponse;
import bflow.wallet.DTO.WalletInfoResponse;
import bflow.wallet.DTO.WalletMemberResponse;
import bflow.wallet.DTO.WalletRequest;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.entities.User;
import bflow.wallet.enums.WalletScope;
import bflow.wallet.repository.RepositoryWallet;
import bflow.wallet.repository.RepositoryWalletUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import bflow.wallet.repository.spec.WalletSpecs;
import org.springframework.data.jpa.domain.Specification;

/**
 * Service class for managing wallet business logic and transactions.
 * Handles wallet operations, balance management, and user access control.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ServiceWallet {

    /** The repository for wallet database operations. */
    private final RepositoryWallet repositoryWallet;

    /** The repository for wallet-user relationship operations. */
    private final RepositoryWalletUser repositoryWalletUser;

    /** The repository for user database operations. */
    private final RepositoryUser repositoryUser;

    /** Service for user business logic operations. */
    private final UserService userService;

    /** Repository for expense persistence operations. */
    private final RepositoryExpense repositoryExpense;

    /** Repository for income persistence operations. */
    private final RepositoryIncome repositoryIncome;

    /** Repository for transfer persistence operations. */
    private final RepositoryTransfers repositoryTransfers;

    /**
     * Repository for recurring transaction persistence operations.
     */
    private final RepositoryRecurringTransaction
            repositoryRecurringTransaction;

    /**
     * Service responsible for enforcing subscription plan limits.
     */
    private final PlanLimitService planLimitService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Maximum number of upcoming recurring transactions to include.
     */
    private static final int UPCOMING_LIMIT = 3;

    /**
     * Busca wallets del usuario autenticado con filtros opcionales por
     * nombre y rol (OWNER = mis wallets, MEMBER = compartidas conmigo).
     * @param userId el usuario autenticado.
     * @param query término de búsqueda por nombre (opcional).
     * @param role filtro de rol; null trae ambas (mías + compartidas).
     * @param scope optional wallet scope filter.
     * @param pageable paginación y sort.
     * @return página de wallets filtradas.
     */
    public Page<WalletResponse> getUserWallets(
            final UUID userId,
            final String query,
            final WalletRole role,
            final WalletScope scope,
            final Pageable pageable
    ) {
        userService.validateUserActive(userId);

        Specification<WalletUser> spec = Specification
                .where(WalletSpecs.byUser(userId))
                .and(WalletSpecs.nameContains(query))
                .and(WalletSpecs.byRole(role))
                .and(WalletSpecs.byScope(scope));

        Page<WalletUser> page = repositoryWalletUser.findAll(spec, pageable);
        return page.map(this::convertToDTO);
    }

    /**
     * Builds the wallet "Information" panel: last activity, highest expense,
     * transaction count, initial value, and the top 3 upcoming recurring items.
     * @param walletId the wallet identifier.
     * @param userId the authenticated user's ID.
     * @return the wallet info response.
     * @throws AccessDeniedException if the user does not have access.
     */
    public WalletInfoResponse getWalletInfo(
            final UUID walletId,
            final UUID userId
    ) {
        userService.validateUserActive(userId);

        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        Wallet wallet = walletUser.getWallet();

        // Last activity: most recent createdAt across the 3 transaction types
        Instant lastExpense = repositoryExpense.findMaxCreatedAtByWalletId(
                walletId
        );

        Instant lastIncome = repositoryIncome.findMaxCreatedAtByWalletId(
                walletId
        );

        Instant lastTransfer = repositoryTransfers.findMaxCreatedAtByWallet(
                walletId
        );

        Instant lastActivity = Stream.of(lastExpense, lastIncome, lastTransfer)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        // Highest single expense by amount
        String highestExpense = repositoryExpense
                .findTopByWalletIdOrderByAmountDesc(walletId)
                .map(Expense::getTitle)
                .orElse(null);

        // Total transaction count (incomes + expenses + transfers)
        long transactions = repositoryExpense.countByWalletId(walletId)
                + repositoryIncome.countByWalletId(walletId)
                + repositoryTransfers.countByWallet(walletId);

        // Top 3 upcoming active recurring transactions
        List<UpcomingTransactionResponse> upcoming =
                repositoryRecurringTransaction
                        .findByWalletIdAndActiveTrueOrderByNextExecutionDateAsc(
                                walletId, PageRequest.of(0, UPCOMING_LIMIT)
                        )
                        .stream()
                        .map(rt -> new UpcomingTransactionResponse(
                                rt.getTitle(),
                                rt.getAmount(),
                                rt.getType(),
                                rt.getNextExecutionDate()
                        ))
                        .toList();

        return new WalletInfoResponse(
                lastActivity,
                highestExpense,
                transactions,
                wallet.getInitialValue(),
                upcoming
        );
    }

    /**
     * Retrieves a wallet by its UUID for an authenticated user.
     * Validates that the user has access to the wallet through WalletUser.
     * @param walletId the UUID of the wallet to retrieve.
     * @param userId the UUID of the authenticated user.
     * @return a WalletResponse DTO containing the wallet data.
     * @throws AccessDeniedException if the user does not have access.
     */
    public WalletResponse getWalletById(
            final UUID walletId,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        // Validate access: check if user is linked to this wallet
        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        return convertToDTO(walletUser);
    }

    /**
     * Retrieves all expenses associated with a specific wallet.
     * Validates that the authenticated user has access to the wallet.
     *
     * @param walletId the wallet unique identifier.
     * @param userId the authenticated user identifier.
     * @param pageable pagination configuration.
     * @return a page containing expense responses.
     * @throws AccessDeniedException if the user has no access to the wallet.
     */
    public Page<ExpenseResponse> getWalletExpenses(
            final UUID walletId,
            final UUID userId,
            final Pageable pageable
    ) {
        // Check if user has an active account
        userService.validateUserActive(userId);

        // Validate wallet access
        repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        Page<Expense> expenses = repositoryExpense
                .findByWalletId(walletId, pageable);

        // Map to DTO
        return expenses.map(this::toExpenseResponse);
    }

    /**
     * Retrieves all incomes associated with a specific wallet.
     * Validates that the authenticated user has access to the wallet.
     *
     * @param walletId the wallet unique identifier.
     * @param userId the authenticated user identifier.
     * @param pageable pagination configuration.
     * @return a page containing income responses.
     * @throws AccessDeniedException if the user has no access to the wallet.
     */
    public Page<IncomeResponse> getWalletIncomes(
            final UUID walletId,
            final UUID userId,
            final Pageable pageable
    ) {
        // Check if user has an active account
        userService.validateUserActive(userId);

        // Validate wallet access
        repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        Page<Income> incomes = repositoryIncome
                .findByWalletId(walletId, pageable);

        // Map to DTO
        return incomes.map(this::toIncomeResponse);
    }

    /**
     * Retrieves all members associated with the specified wallet.
     *
     * The authenticated user must already belong to the wallet in order
     * to access the member list.
     *
     * @param walletId the wallet UUID
     * @param currentUserId the authenticated user's UUID
     * @return a list of wallet members
     * @throws AccessDeniedException if the user does not belong to the wallet
     */
    public List<WalletMemberResponse> getWalletMembers(
            final UUID walletId,
            final UUID currentUserId
    ) {
        userService.validateUserActive(currentUserId);

        repositoryWalletUser
                .findByWalletIdAndUserId(walletId, currentUserId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                messageService.get("wallet.accessDenied")
                        ));

        return repositoryWalletUser
                .findByWalletId(walletId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    /**
     * Creates a new wallet for an authenticated user.
     * Sets the authenticated user as OWNER through WalletUser.
     * @param request the wallet creation request.
     * @param userId the UUID of the authenticated user.
     * @return a WalletResponse DTO containing the created wallet data.
     * @throws IllegalArgumentException if the user does not exist.
     */
    public WalletResponse createWallet(
            final WalletRequest request,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        planLimitService.assertCanCreate(
                userId, FeatureCodes.WALLETS,
                repositoryWalletUser.countByUserIdAndRole(
                        userId, WalletRole.OWNER
                ));

        // Retrieve authenticated user
        User user = repositoryUser.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageService.get("user.notFound")
                ));

        // Create Wallet entity with proper BigDecimal handling
        Wallet wallet = new Wallet();
        wallet.setName(request.getName().trim());
        wallet.setDescription(request.getDescription());
        wallet.setCurrency(request.getCurrency());

        // Set balance = initialValue with proper scale and rounding
        BigDecimal initialValue = request.getInitialValue()
                .setScale(2, RoundingMode.HALF_EVEN);
        wallet.setInitialValue(initialValue);
        wallet.setBalance(initialValue);

        // Save Wallet
        Wallet savedWallet = repositoryWallet.saveAndFlush(wallet);

        // Create WalletUser relationship with OWNER role
        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(savedWallet);
        walletUser.setUser(user);
        walletUser.setRole(WalletRole.OWNER);

        // Save WalletUser
        repositoryWalletUser.save(walletUser);

        // Map Wallet entity to WalletResponse DTO
        return convertToDTO(walletUser);
    }

    /**
     * Updates an existing wallet with new information.
     * Only the wallet owner can update wallet details.
     * @param walletId the unique identifier of the wallet to update.
     * @param request the wallet update request containing new values.
     * @param userId the unique identifier of the authenticated user.
     * @return the updated wallet response.
     * @throws AccessDeniedException if the user is not the wallet owner.
     */
    public WalletResponse patchWallet(
            final UUID walletId,
            @Valid final UpdateWalletRequest request,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        // Retrieve wallet
        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageService.get("wallet.accessDenied")
                ));

        Wallet wallet = walletUser.getWallet();

        if (walletUser.getRole() != WalletRole.OWNER) {
            String errorMessage = messageService.get("wallet.owner.onlyUpdate");
            throw new AccessDeniedException(errorMessage);
        }

        // Update fields
        if (request.getName() != null) {
            wallet.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            wallet.setDescription(request.getDescription());
        }

        return convertToDTO(walletUser);
    }

    /**
     * Converts a WalletUser entity to a WalletResponse DTO.
     * @param walletUser the wallet-user relationship.
     * @return the wallet response DTO.
     */
    public WalletResponse convertToDTO(final WalletUser walletUser) {

        Wallet wallet = walletUser.getWallet();

        WalletResponse dto = new WalletResponse();
        dto.setId(wallet.getId());
        dto.setName(wallet.getName());
        dto.setDescription(wallet.getDescription());
        dto.setCurrency(wallet.getCurrency());
        dto.setBalance(wallet.getBalance());
        dto.setRole(walletUser.getRole());
        dto.setInitialValue(wallet.getInitialValue());
        dto.setMemberCount(
                Math.toIntExact(
                        repositoryWalletUser.countByWalletId(wallet.getId())
                )
        );
        dto.setCreatedAt(wallet.getCreatedAt());
        dto.setUpdatedAt(wallet.getUpdatedAt());

        return dto;
    }

    /**
     * Adds the specified amount to the wallet's balance.
     *
     * @param wallet the wallet to update (not null).
     * @param amount the amount to add (must be non-negative).
     * @throws IllegalArgumentException if amount is negative.
     */
    public void addBalance(final Wallet wallet, final BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get("wallet.amount.negative", amount)
            );
        }
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
    }

    /**
     * Subtracts the specified amount from the wallet's balance.
     *
     * @param wallet the wallet to update (not null).
     * @param amount the amount to subtract (must be non-negative).
     * @throws IllegalArgumentException if amount is negative or
     *         if balance would become negative.
     */
    public void subtractBalance(final Wallet wallet, final BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get("wallet.amount.negative", amount)
            );
        }
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get(
                            "wallet.balance.insufficient", wallet.getBalance()
                    )
            );
        }
        wallet.setBalance(newBalance);
    }

    /**
     * Adjusts the wallet balance for an INCOME update on the same
     * wallet. An increase in amount increases the balance (more
     * income = more money); a decrease reduces it.
     *
     * @param wallet the wallet to update (not null).
     * @param oldAmount the previous income amount (not null).
     * @param newAmount the new income amount (not null).
     * @throws IllegalArgumentException if oldAmount or newAmount
     *         are negative, or if adjusted balance would be negative.
     */
    public void adjustBalanceForIncomeUpdate(
            final Wallet wallet,
            final BigDecimal oldAmount,
            final BigDecimal newAmount
    ) {
        if (oldAmount.signum() < 0 || newAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get("wallet.amount.nonNegative")
            );
        }

        // Undo the old income's impact, then apply the new one.
        BigDecimal difference = newAmount.subtract(oldAmount);
        BigDecimal adjustedBalance = wallet.getBalance().add(difference);

        if (adjustedBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get("wallet.balance.insufficientForAdjustment")
            );
        }

        wallet.setBalance(adjustedBalance);
    }

    /**
     * Adjusts the wallet balance for an EXPENSE update on the same
     * wallet. This is the mirror image of
     * {@link #adjustBalanceForIncomeUpdate}: an increase in amount
     * DECREASES the balance (more spent = less remaining), and a
     * decrease INCREASES it. Sharing one generic method between
     * income and expense updates previously caused every same-wallet
     * expense edit to move the balance in the wrong direction.
     *
     * @param wallet the wallet to update (not null).
     * @param oldAmount the previous expense amount (not null).
     * @param newAmount the new expense amount (not null).
     * @throws IllegalArgumentException if oldAmount or newAmount
     *         are negative, or if adjusted balance would be negative.
     */
    public void adjustBalanceForExpenseUpdate(
            final Wallet wallet,
            final BigDecimal oldAmount,
            final BigDecimal newAmount
    ) {
        if (oldAmount.signum() < 0 || newAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get("wallet.amount.nonNegative")
            );
        }

        // Undo the old expense's impact (add it back), then apply
        // the new one (subtract it).
        BigDecimal adjustedBalance = wallet.getBalance()
                .add(oldAmount)
                .subtract(newAmount);

        if (adjustedBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get("wallet.balance.insufficientForAdjustment")
            );
        }

        wallet.setBalance(adjustedBalance);
    }

    /**
     * Reverses the impact of a transaction on the wallet balance.
     * Used when a transaction is deleted.
     *
     * @param wallet the wallet to update (not null).
     * @param amount the transaction amount to reverse (not null).
     * @throws IllegalArgumentException if amount is negative.
     */
    public void reverseTransactionImpact(
            final Wallet wallet,
            final BigDecimal amount
    ) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    messageService.get("wallet.amount.negative", amount)
            );
        }
        // Add back the amount since we're reversing an expense
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
    }

    /**
     * Creates the default wallet assigned to a newly registered user.
     *
     * @param user owner of the wallet
     * @return the existing owner wallet if one already exists, otherwise the
     *         newly created default wallet
     */
    @Transactional
    public Wallet createDefaultWallet(
            final User user
    ) {

        if (repositoryWalletUser.existsByUserId(user.getId())) {
            return repositoryWalletUser
                    .findFirstByUserIdAndRole(
                            user.getId(),
                            WalletRole.OWNER
                    )
                    .map(WalletUser::getWallet)
                    .orElseThrow();
        }

        Wallet wallet = new Wallet();

        wallet.setName("Personal");
        wallet.setDescription("Default wallet");
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setInitialValue(BigDecimal.ZERO);

        repositoryWallet.save(wallet);

        WalletUser walletUser = new WalletUser();

        walletUser.setWallet(wallet);
        walletUser.setUser(user);
        walletUser.setRole(WalletRole.OWNER);

        repositoryWalletUser.save(walletUser);

        return wallet;
    }

    /**
     * Permanently deletes a wallet only when it has no financial history.
     *
     * @param walletId wallet identifier.
     * @param userId authenticated user identifier.
     */
    public void deleteWallet(
            final UUID walletId,
            final UUID userId
    ) {
        userService.validateUserActive(userId);

        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new AccessDeniedException(
                        messageService.get("wallet.accessDenied")
                ));

        if (walletUser.getRole() != WalletRole.OWNER) {
            throw new AccessDeniedException(
                    messageService.get("wallet.owner.onlyDelete")
            );
        }

        Wallet wallet = walletUser.getWallet();

        if (hasFinancialHistory(walletId)) {
            throw new IllegalStateException(
                    messageService.get("wallet.cannotDelete.hasHistory")
            );
        }

        repositoryWalletUser.delete(walletUser);
        repositoryWallet.delete(wallet);
    }

    private boolean hasFinancialHistory(final UUID walletId) {
        return repositoryExpense.countByWalletId(walletId) > 0
                || repositoryIncome.countByWalletId(walletId) > 0
                || repositoryTransfers.countByWallet(walletId) > 0
                || repositoryRecurringTransaction
                .existsByWalletId(walletId);
    }

    /**
     * Converts an Expense entity into an ExpenseResponse DTO.
     *
     * @param expense the expense entity to convert.
     * @return the mapped expense response DTO.
     */
    public ExpenseResponse toExpenseResponse(final Expense expense) {
        ExpenseResponse dto = new ExpenseResponse();
        dto.setId(expense.getId().toString());
        dto.setTitle(expense.getTitle());
        dto.setDescription(expense.getDescription());
        dto.setAmount(expense.getAmount());
        dto.setDate(expense.getDate());
        dto.setCategory(
                TransactionMapper.mapCategoryToResponse(expense.getCategory())
        );
        dto.setTaxDeductible(expense.getTaxDeductible());
        dto.setRecurring(expense.getRecurring());
        dto.setReimbursable(expense.getReimbursable());
        dto.setWalletId(expense.getWallet().getId().toString());
        dto.setWalletName(expense.getWallet().getName());
        dto.setContributorId(expense.getContributor().getId().toString());
        dto.setContributorName(expense.getContributor().getEmail());
        dto.setCreatedAt(expense.getCreatedAt());
        return dto;
    }

    /**
     * Converts an Income entity into an IncomeResponse DTO.
     * @param income the income entity to convert.
     * @return the mapped income response DTO.
     */
    public IncomeResponse toIncomeResponse(final Income income) {
        IncomeResponse dto = new IncomeResponse();
        dto.setId(income.getId().toString());
        dto.setTitle(income.getTitle());
        dto.setDescription(income.getDescription());
        dto.setAmount(income.getAmount());
        dto.setDate(income.getDate());
        dto.setCategory(
                TransactionMapper.mapCategoryToResponse(income.getCategory())
        );
        dto.setWalletId(income.getWallet().getId().toString());
        dto.setWalletName(income.getWallet().getName());
        dto.setContributorId(income.getContributor().getId().toString());
        dto.setContributorName(income.getContributor().getEmail());
        dto.setCreatedAt(income.getCreatedAt());
        return dto;
    }

    private WalletMemberResponse toMemberResponse(
            final WalletUser walletUser
    ) {

        return new WalletMemberResponse(
                walletUser.getUser().getId(),
                walletUser.getUser().getEmail(),
                walletUser.getUser().getName(),
                walletUser.getUser().getPictureUrl(),
                walletUser.getRole(),
                walletUser.getCreatedAt()
        );
    }
}
