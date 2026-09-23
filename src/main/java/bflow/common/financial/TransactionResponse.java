package bflow.common.financial;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Unified transaction history entry (BFF response), combining
 * incomes, expenses, and transfers into a single shape for the frontend.
 */
@Getter
@Setter
public class TransactionResponse {

    /** The transaction identifier. */
    private String id;

    /** The origin type: INCOME, EXPENSE, or TRANSFER. */
    private TransactionType type;

    /** Title (category-like label; "Transfer" for transfers). */
    private String title;

    /** Optional description. */
    private String description;

    /**
     * Signed amount: negative for expenses, positive for incomes.
     * For transfers: signed relative to the queried wallet when a
     * single wallet is in scope, otherwise positive/absolute.
     */
    private BigDecimal amount;

    /** Transaction date (used for sorting, newest first). */
    private Instant date;

    /** Wallet this entry belongs to (or source wallet, for transfers). */
    private String walletId;

    /** Wallet name. */
    private String walletName;

    /** Destination wallet id (transfers only, null otherwise). */
    private String counterpartWalletId;

    /** Destination wallet name (transfers only, null otherwise). */
    private String counterpartWalletName;

    /** Category id (incomes/expenses only). */
    private String categoryId;

    /** Category name. */
    private String categoryName;

    /** Category icon. */
    private String categoryIcon;

    /** Category color. */
    private String categoryColor;

    /** Contributor id. */
    private String contributorId;

    /**
     * Contributor's display name, only populated when the wallet has
     * more than one member — pointless noise on a solo wallet.
     */
    private String contributorName;

    /**
     * Contributor's email, same visibility rule as
     * {@link #contributorName}.
     */
    private String contributorEmail;

    /**
     * Contributor's profile picture URL, same visibility rule as
     * {@link #contributorName}.
     */
    private String contributorPictureUrl;

    /** Transfer status (transfers only, null otherwise). */
    @Schema(description = "Transfer processing state when type is TRANSFER.",
            allowableValues = {"COMPLETED"})
    private String status;

    /** Entry source (manual, receipt, voice, import) — null for transfers. */
    private String source;
}
