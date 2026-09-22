package bflow.expenses;

import bflow.auth.entities.User;
import bflow.dashboard.projection.CategorySpendingProjection;
import bflow.dashboard.projection.MonthlyTotalProjection;
import bflow.expenses.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryExpense extends JpaRepository<Expense, UUID> {
    /**
    * Retrieves expenses belonging to a specific wallet.
    *
    * @param walletId the wallet identifier.
    * @param pageable pagination configuration.
    * @return a page containing wallet expenses.
    */
    Page<Expense> findByWalletId(UUID walletId, Pageable pageable);

    /**
     * Sum expenses for a wallet within a date range.
     *
     * @param walletId the wallet ID
     * @param start the start date
     * @param end the end date
     * @return the sum of expenses
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.wallet.id = :walletId
        AND e.date BETWEEN :start AND :end
    """)
    BigDecimal sumExpensesByWalletAndDateRange(
            UUID walletId,
            LocalDate start,
            LocalDate end
    );

    /**
     * Sum expenses for a wallet and category within a date range.
     *
     * @param walletId the wallet ID
     * @param categoryId the category ID
     * @param start the start date
     * @param end the end date
     * @return the sum of expenses
     */
    @Query("""
    SELECT COALESCE(SUM(e.amount), 0)
    FROM Expense e
    WHERE e.wallet.id = :walletId
    AND e.category.id = :categoryId
    AND e.date BETWEEN :start AND :end
""")
    BigDecimal sumByWalletAndCategoryAndDateRange(
            UUID walletId, UUID categoryId, LocalDate start, LocalDate end);

    /**
     * Sum expenses for a category across a set of wallets within a date
     * range — used for CATEGORY_GLOBAL budgets.
     *
     * @param walletIds the wallet IDs the user participates in
     * @param categoryId the category ID
     * @param start the start date
     * @param end the end date
     * @return the sum of expenses
     */
    @Query("""
    SELECT COALESCE(SUM(e.amount), 0)
    FROM Expense e
    WHERE e.wallet.id IN :walletIds
    AND e.category.id = :categoryId
    AND e.date BETWEEN :start AND :end
""")
    BigDecimal sumByWalletsAndCategoryAndDateRange(
            List<UUID> walletIds,
            UUID categoryId,
            LocalDate start,
            LocalDate end
    );

    /**
     * Retrieves the expense with the highest amount for a wallet.
     *
     * @param walletId wallet identifier.
     * @return the highest expense, if one exists.
     */
    Optional<Expense> findTopByWalletIdOrderByAmountDesc(UUID walletId);

    /**
     * Counts the total number of expenses for a wallet.
     *
     * @param walletId wallet identifier.
     * @return total expense count.
     */
    long countByWalletId(UUID walletId);

    /**
     * Retrieves the latest expense creation timestamp for a wallet.
     *
     * @param walletId wallet identifier.
     * @return most recent creation timestamp, or {@code null} if no expenses
     *         exist.
     */
    @Query(
        "SELECT MAX(e.createdAt) FROM Expense e WHERE e.wallet.id = :walletId"
    )
    Instant findMaxCreatedAtByWalletId(UUID walletId);

    /**
     * Finds expenses for a wallet within a date range, ordered chronologically.
     *
     * @param walletId the wallet ID
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @return matching expenses ordered by date ascending
     */
    List<Expense> findByWalletIdAndDateBetweenOrderByDateAsc(
            UUID walletId,
            LocalDate start,
            LocalDate end
    );

    /**
     * Finds expenses for a wallet and category within a date range,
     * ordered chronologically.
     *
     * @param walletId the wallet ID
     * @param categoryId the category ID
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @return matching expenses ordered by date ascending
     */
    List<Expense> findByWalletIdAndCategoryIdAndDateBetweenOrderByDateAsc(
            UUID walletId,
            UUID categoryId,
            LocalDate start,
            LocalDate end
    );

    /**
     * Finds expenses for a wallet within a date range, ordered by most
     * recent first — used for "recent activity" bounded to a period.
     *
     * @param walletId the wallet ID
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @param pageable pagination configuration (use to limit results)
     * @return matching expenses ordered by date descending
     */
    List<Expense>
    findByWalletIdAndDateBetweenOrderByDateDescCreatedAtDesc(
            UUID walletId,
            LocalDate start,
            LocalDate end,
            Pageable pageable
    );

    /**
     * Finds expenses for a wallet and category within a date range,
     * ordered by most recent first — used for "recent activity" bounded
     * to a period.
     *
     * @param walletId the wallet ID
     * @param categoryId the category ID
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @param pageable pagination configuration (use to limit results)
     * @return matching expenses ordered by date descending
     */
    List<Expense>
    findByWalletIdAndCategoryIdAndDateBetweenOrderByDateDescCreatedAtDesc(
            UUID walletId,
            UUID categoryId,
            LocalDate start,
            LocalDate end,
            Pageable pageable
    );

    /**
     * Finds expenses for a category across a set of wallets within a date
     * range, ordered chronologically.
     *
     * @param walletIds the wallet IDs to search
     * @param categoryId the category ID
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @return matching expenses ordered by date ascending
     */
    List<Expense>
    findByWalletIdInAndCategoryIdAndDateBetweenOrderByDateAsc(
            List<UUID> walletIds,
            UUID categoryId,
            LocalDate start,
            LocalDate end
    );

    /**
     * Finds expenses for a category across a set of wallets within a date
     * range, ordered by most recent first.
     *
     * @param walletIds the wallet IDs to search
     * @param categoryId the category ID
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @param pageable pagination configuration
     * @return matching expenses ordered by date descending
     */
    List<Expense>
    findByWalletIdInAndCategoryIdAndDateBetweenOrderByDateDescCreatedAtDesc(
            List<UUID> walletIds,
            UUID categoryId,
            LocalDate start,
            LocalDate end,
            Pageable pageable
    );

    /**
     * Sums expenses across a set of wallets within a date range.
     *
     * @param walletIds the wallet IDs to search
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @return the sum of expenses
     */
    @Query("""
    SELECT COALESCE(SUM(e.amount), 0)
    FROM Expense e
    WHERE e.wallet.id IN :walletIds
    AND e.date BETWEEN :start AND :end
""")
    BigDecimal sumByWalletsAndDateRange(
            List<UUID> walletIds, LocalDate start, LocalDate end);

    /**
     * Groups expenses by month within a date range.
     *
     * @param walletIds the wallet IDs to search
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @return monthly expense totals
     */
    @Query("""
    SELECT EXTRACT(MONTH FROM e.date) as month,
           COALESCE(SUM(e.amount), 0) as total
    FROM Expense e
    WHERE e.wallet.id IN :walletIds
    AND e.date BETWEEN :start AND :end
    GROUP BY EXTRACT(MONTH FROM e.date)
""")
    List<MonthlyTotalProjection> sumGroupedByMonth(
            List<UUID> walletIds, LocalDate start, LocalDate end);

    /**
     * Retrieves the most recent expenses across a set of wallets.
     *
     * @param walletIds the wallet IDs to search
     * @param pageable pagination configuration
     * @return expenses ordered by creation date descending
     */
    List<Expense> findByWalletIdInOrderByCreatedAtDesc(
            List<UUID> walletIds, Pageable pageable);

    /**
     * Groups expenses by category within a date range.
     *
     * @param walletIds the wallet IDs to search
     * @param start range start (inclusive)
     * @param end range end (inclusive)
     * @param pageable pagination configuration
     * @return category spending totals ordered by amount descending
     */
    @Query("""
    SELECT e.category.id as categoryId,
           e.category.name as categoryName,
           COALESCE(SUM(e.amount), 0) as total
    FROM Expense e
    WHERE e.wallet.id IN :walletIds
    AND e.date BETWEEN :start AND :end
    GROUP BY e.category.id, e.category.name
    ORDER BY SUM(e.amount) DESC
""")
    List<CategorySpendingProjection> sumGroupedByCategory(
            List<UUID> walletIds,
            LocalDate start,
            LocalDate end,
            Pageable pageable
    );

    /**
     * Counts expenses across the given wallets within a date range.
     *
     * @param walletIds the wallet IDs to filter by
     * @param start the start date of the range (inclusive)
     * @param end the end date of the range (inclusive)
     * @return the number of expenses in the given wallets and date range
     */
    @Query("SELECT COUNT(e) FROM Expense e "
            + "WHERE e.wallet.id IN :walletIds "
            + "AND e.date BETWEEN :start AND :end")
    long countByWalletsAndDateRange(
            @Param("walletIds") List<UUID> walletIds,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Reassigns every expense contributed by {@code fromUserId} within
     * the given wallets to {@code toUser} — used when hard-deleting an
     * account to preserve shared-wallet history under the system
     * "Usuario de Bflow" placeholder.
     *
     * @param fromUserId the user being hard-deleted
     * @param toUser the placeholder user to reassign to
     * @param walletIds the shared wallets that survive the deletion
     * @return the number of rows updated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Expense e SET e.contributor = :toUser "
            + "WHERE e.contributor.id = :fromUserId "
            + "AND e.wallet.id IN :walletIds")
    int reassignContributor(
            @Param("fromUserId") UUID fromUserId,
            @Param("toUser") User toUser,
            @Param("walletIds") List<UUID> walletIds
    );

    List<Expense> findByWalletIdIn(List<UUID> walletIds);
    void deleteByWalletIdIn(List<UUID> walletIds);
}
