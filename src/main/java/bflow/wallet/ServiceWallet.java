package bflow.wallet;

import bflow.auth.services.UserService;
import bflow.auth.services.UserServiceImpl;
import bflow.wallet.DTO.WalletRequest;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.WalletRole;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.entities.User;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Service class for managing wallet business logic and transactions.
 */
@Service
@Transactional
@AllArgsConstructor
public class ServiceWallet {

    /** The repository for wallet database operations. */
    private final RepositoryWallet repositoryWallet;

    /** The repository for wallet-user relationship operations. */
    private final RepositoryWalletUser repositoryWalletUser;

    /** The repository for user database operations. */
    private final RepositoryUser repositoryUser;

    private final UserServiceImpl userService;

    /**
     * Retrieves all wallets for a user with pagination.
     * @param userId the user ID.
     * @param pageable the pagination information.
     * @return a page of wallet responses.
     */
    public Page<WalletResponse> getUserWallets(
            final UUID userId,
            final Pageable pageable
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        Page<WalletUser> page = repositoryWalletUser
                .findByUserId(userId, pageable);
        return page.map(this::convertToDTO);
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
                        "User does not have access to this wallet"
                ));

        // Retrieve the wallet from the WalletUser relationship
        Wallet wallet = walletUser.getWallet();

        // Map Wallet entity to WalletResponse DTO
        return WalletResponse.builder()
                .id(wallet.getId())
                .name(wallet.getName())
                .description(wallet.getDescription())
                .currency(wallet.getCurrency())
                .balance(wallet.getBalance())
                .initialValue(wallet.getInitialValue())
                .createdAt(wallet.getCreatedAt())
                .build();
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

        // Retrieve authenticated user
        User user = repositoryUser.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found"
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
        Wallet savedWallet = repositoryWallet.save(wallet);

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

    public WalletResponse patchWallet(
            final UUID walletId,
            @Valid final WalletRequest request,
            final UUID userId
    ) {
        //Check if user has an active account
        userService.validateUserActive(userId);

        // Retrieve wallet
        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User does not have access to this wallet"
                ));

        Wallet wallet = walletUser.getWallet();

        if (walletUser.getRole() != WalletRole.OWNER) {
            throw new AccessDeniedException("Only owners can update the wallet");
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
    private WalletResponse convertToDTO(final WalletUser walletUser) {

        Wallet wallet = walletUser.getWallet();

        WalletResponse dto = new WalletResponse();
        dto.setId(wallet.getId());
        dto.setName(wallet.getName());
        dto.setDescription(wallet.getDescription());
        dto.setCurrency(wallet.getCurrency());
        dto.setBalance(wallet.getBalance());
        dto.setRole(walletUser.getRole());
        dto.setInitialValue(wallet.getInitialValue());
        dto.setCreatedAt(wallet.getCreatedAt());

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
                    "Amount cannot be negative: " + amount
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
                    "Amount cannot be negative: " + amount
            );
        }
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance: " + wallet.getBalance()
            );
        }
        wallet.setBalance(newBalance);
    }

    /**
     * Adjusts the wallet balance for an update transaction.
     * Reverses the old amount and applies the new amount.
     *
     * @param wallet the wallet to update (not null).
     * @param oldAmount the previous transaction amount (not null).
     * @param newAmount the new transaction amount (not null).
     * @throws IllegalArgumentException if oldAmount or newAmount
     *         are negative, or if adjusted balance would be negative.
     */
    public void adjustBalanceForUpdate(
            final Wallet wallet,
            final BigDecimal oldAmount,
            final BigDecimal newAmount
    ) {
        if (oldAmount.signum() < 0 || newAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amounts must be non-negative"
            );
        }

        // Reverse the impact of old amount
        BigDecimal difference = newAmount.subtract(oldAmount);
        BigDecimal adjustedBalance = wallet.getBalance().add(difference);

        if (adjustedBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance for adjustment"
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
                    "Amount cannot be negative: " + amount
            );
        }
        // Add back the amount since we're reversing an expense
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
    }
}
