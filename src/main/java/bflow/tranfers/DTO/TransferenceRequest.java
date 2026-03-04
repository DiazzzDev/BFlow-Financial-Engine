package bflow.tranfers.DTO;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for transfer request data.
 */
@Getter
@Setter
public class TransferenceRequest {
    /** The UUID of the source wallet. */
    @NotNull
    private UUID fromWalletId;

    /** The UUID of the destination wallet. */
    @NotNull
    private UUID toWalletId;

    /** The transfer amount. */
    @NotNull
    private BigDecimal amount;

    /** Optional transfer description. */
    @Nullable
    private String description;

}
