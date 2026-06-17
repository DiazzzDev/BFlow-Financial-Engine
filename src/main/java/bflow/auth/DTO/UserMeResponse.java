package bflow.auth.DTO;

import bflow.auth.DTO.user.UserProfileResponse;
import bflow.subscription.dto.SubscriptionResponse;
import bflow.wallet.DTO.WalletResponse;

import java.util.List;
import java.util.UUID;

public record UserMeResponse(
        UUID id,
        String email,
        List<String> roles,
        SubscriptionResponse subscription,
        List<WalletResponse> wallets,
        UserProfileResponse profile
) {
}
