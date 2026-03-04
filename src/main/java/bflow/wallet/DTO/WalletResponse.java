package bflow.wallet.DTO;

import bflow.wallet.enums.WalletRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import bflow.wallet.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for wallet response information.
 * Provides a clean view of wallet data without exposing internal entities.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class WalletResponse {

    /** The unique identifier of the wallet. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID id;

    /** The display name of the wallet. */
    private String name;

    /** The description of the wallet. */
    private String description;

    /** The currency code of the wallet. */
    private Currency currency;

    /** The current available balance. */
    private BigDecimal balance;

    /** The initial value the wallet started with. */
    private BigDecimal initialValue;

    /** The timestamp when the wallet was created. */
    private Instant createdAt;

    /** The role of the current user in this wallet. */
    private WalletRole role;
}
