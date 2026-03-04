package bflow.tranfers.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for transfer response data.
 */
@Getter
@Setter
public class TransferenceResponse {
    /** The transfer identifier. */
    private UUID id;

    /** The source wallet identifier. */
    private UUID fromWalletId;
    /** The source wallet name. */
    private String fromWalletName;

    /** The destination wallet identifier. */
    private UUID toWalletId;
    /** The destination wallet name. */
    private String toWalletName;

    /** The transferred amount. */
    private BigDecimal amount;

    /** Optional transfer description. */
    private String description;

    /** The transfer status. */
    private String status;
}
