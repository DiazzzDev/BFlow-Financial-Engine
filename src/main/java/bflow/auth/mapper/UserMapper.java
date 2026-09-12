package bflow.auth.mapper;

import bflow.auth.DTO.UserMeResponse;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.User;
import bflow.subscription.dto.SubscriptionResponse;
import bflow.subscription.entities.Subscription;
import bflow.subscription.enums.SubscriptionStatus;
import bflow.subscription.repository.RepositorySubscription;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps User entities to their public-facing response DTOs.
 */
@Component
@RequiredArgsConstructor
public class UserMapper {

    /**
     * Repository used to resolve the user's subscription.
     */
    private final RepositorySubscription repositorySubscription;

    /**
     * Repository used to resolve the user's wallet memberships.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Service used to map a wallet membership into its public DTO.
     */
    private final ServiceWallet serviceWallet;

    /**
     * Builds the "/auth/me" response for the given user.
     *
     * @param user the authenticated user entity
     * @return the assembled response DTO
     */
    public UserMeResponse toMeResponse(final User user) {
        return new UserMeResponse(
                user.getId(),
                List.copyOf(user.getRoles()),
                resolveSubscription(user.getId()),
                resolveWallets(user.getId()),
                toProfileResponse(user)
        );
    }

    /**
     * Resolves the user's current subscription — active if one
     * exists, otherwise past-due, otherwise {@code null} (a user can
     * legitimately have neither yet, e.g. right before the
     * registration bootstrap that grants the free plan runs).
     *
     * @param userId the user identifier
     * @return the current subscription, or {@code null}
     */
    private SubscriptionResponse resolveSubscription(final UUID userId) {
        Optional<Subscription> subscription = repositorySubscription
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .or(() -> repositorySubscription.findByUserIdAndStatus(
                        userId, SubscriptionStatus.PAST_DUE
                ));

        return subscription.map(SubscriptionResponse::from).orElse(null);
    }

    /**
     * Resolves every wallet the user belongs to.
     *
     * @param userId the user identifier
     * @return the user's wallets
     */
    private List<WalletResponse> resolveWallets(final UUID userId) {
        return repositoryWalletUser.findByUserId(userId)
                .stream()
                .map(serviceWallet::convertToDTO)
                .toList();
    }

    /**
     * Maps a User entity to its public profile representation.
     *
     * @param user the user entity
     * @return the mapped profile response
     */
    public UserProfileResponse toProfileResponse(final User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .pictureUrl(user.getPictureUrl())
                .roles(user.getRoles())
                .status(user.getStatus())
                .language(user.getLanguage())
                .build();
    }
}
