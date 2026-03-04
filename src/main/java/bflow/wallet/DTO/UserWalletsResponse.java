package bflow.wallet.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * DTO for user wallets response data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserWalletsResponse {
    /** The user associated with the wallets. */
    private UUID userId;
    /** The user's email address. */
    private String userEmail;

    /** The list of wallets associated with the user. */
    private List<WalletResponse> wallets;
}
