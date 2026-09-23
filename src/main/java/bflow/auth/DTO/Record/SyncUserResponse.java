package bflow.auth.DTO.Record;

import bflow.auth.DTO.user.UserProfileResponse;
import bflow.subscription.dto.CurrentSubscriptionResponse;
import bflow.subscription.dto.SubscriptionResponse;
import bflow.wallet.DTO.WalletResponse;

import java.util.List;
import java.util.UUID;

/**
 * Response returned after synchronizing an authenticated user.
 *
 * @param id user identifier
 * @param email user email
 * @param roles user roles
 * @param isNewUser whether the user was created during synchronization
 * @param subscription current user subscription
 * @param plan the user's current plan — code, name, status, and the
 *         feature flags/limits it grants, mirroring what
 *         {@code GET /api/v1/subscriptions/current} returns.
 *         {@code null} if it couldn't be resolved (should not
 *         normally happen; every user gets a free plan on
 *         registration).
 * @param wallets wallets belonging to the user
 * @param profile user profile information
 */
public record SyncUserResponse(
        UUID id,
        String email,
        List<String> roles,
        boolean isNewUser,
        SubscriptionResponse subscription,
        CurrentSubscriptionResponse plan,
        List<WalletResponse> wallets,
        UserProfileResponse profile,
        boolean accountPendingDeletion,
        Long deletionDaysRemaining
) { }