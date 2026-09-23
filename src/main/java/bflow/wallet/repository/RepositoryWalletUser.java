package bflow.wallet.repository;

import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.Currency;
import bflow.wallet.enums.WalletRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryWalletUser extends JpaRepository<WalletUser, UUID>,
        JpaSpecificationExecutor<WalletUser> {
    /**
     * Finds a wallet-user relationship by wallet ID and user ID.
     * @param walletId the wallet UUID.
     * @param userId the user UUID.
     * @return optional wallet-user relationship.
     */
    Optional<WalletUser> findByWalletIdAndUserId(UUID walletId, UUID userId);

    /**
     * Find the first wallet-user relationship for a user with a specific role.
     *
     * @param userId the user UUID
     * @param role the wallet role
     * @return optional wallet-user relationship
     */
    Optional<WalletUser> findFirstByUserIdAndRole(
            UUID userId,
            WalletRole role
    );

    /**
     * Checks whether the user belongs to at least one wallet.
     *
     * @param userId user identifier
     * @return true if a wallet association exists
     */
    boolean existsByUserId(UUID userId);

    /**
     * Counts the number of wallets where the user has the specified role.
     *
     * @param userId the user UUID
     * @param role the wallet role
     * @return the number of matching wallet memberships
     */
    long countByUserIdAndRole(UUID userId, WalletRole role);

    /**
     * Counts the total number of members in a wallet.
     *
     * @param walletId the wallet UUID
     * @return the total number of wallet members
     */
    long countByWalletId(UUID walletId);

    /**
     * Checks whether a user belongs to the specified wallet.
     *
     * @param walletId the wallet UUID
     * @param userId the user UUID
     * @return {@code true} if the user belongs to the wallet,
     * otherwise {@code false}
     */
    boolean existsByWalletIdAndUserId(
            UUID walletId,
            UUID userId
    );

    /**
     * Checks whether a user with the specified email belongs to the wallet.
     *
     * @param walletId the wallet UUID
     * @param email the user's email address
     * @return {@code true} if the user belongs to the wallet,
     * otherwise {@code false}
     */
    boolean existsByWalletIdAndUserEmail(
            UUID walletId,
            String email
    );

    /**
     * Finds all members associated with the specified wallet.
     *
     * @param walletId the wallet UUID
     * @return a list of wallet members
     */
    List<WalletUser> findByWalletId(UUID walletId);

    /**
     * Retrieves the identifiers of all wallets associated with a user.
     *
     * @param userId the user UUID.
     * @return a list containing the wallet identifiers.
     */
    @Query(
            "SELECT wu.wallet.id FROM WalletUser wu WHERE wu.user.id = :userId"
    )
    List<UUID> findWalletIdsByUserId(UUID userId);

    /**
     * Finds every wallet-user relationship for a given user, across
     * all wallets they belong to. Used to build the "my wallets"
     * list on login/session sync.
     *
     * @param userId the user UUID
     * @return the user's wallet memberships
     */
    List<WalletUser> findByUserId(UUID userId);

    /**
     * Retrieves the identifiers of all wallets associated with a
     * user, filtered to a specific currency. Used by CATEGORY_GLOBAL
     * budgets so spend is only summed across wallets denominated in
     * the same currency as the budget itself — summing raw amounts
     * across different currencies (e.g. MXN and USD) would be
     * financially meaningless.
     *
     * @param userId the user UUID.
     * @param currency the currency to filter wallets by.
     * @return a list containing the matching wallet identifiers.
     */
    @Query(
            "SELECT wu.wallet.id FROM WalletUser wu "
            + "WHERE wu.user.id = :userId AND wu.wallet.currency = :currency"
    )
    List<UUID> findWalletIdsByUserIdAndCurrency(
            UUID userId, Currency currency);

    /**
     * Retrieves the email addresses of every member of the specified
     * wallet. Used to resolve collaborator search status in bulk
     * instead of querying per candidate.
     *
     * @param walletId the wallet UUID
     * @return the member email addresses
     */
    @Query(
            "SELECT wu.user.email FROM WalletUser wu "
                    + "WHERE wu.wallet.id = :walletId"
    )
    List<String> findMemberEmailsByWalletId(UUID walletId);

    void deleteByUserId(UUID userId);
}
