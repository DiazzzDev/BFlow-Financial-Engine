package bflow.auth.services;

import bflow.auth.entities.User;
import bflow.subscription.services.SubscriptionService;
import bflow.wallet.ServiceWallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthBootstrapServiceImpl implements AuthBootstrapService {

    /**
     * Wallet service.
     */
    private final ServiceWallet serviceWallet;

    /**
     * Subscription service.
     */
    private final SubscriptionService subscriptionService;

    /**
     * Creates default resources required for a newly registered user.
     *
     * @param user user being initialized
     */
    @Override
    public void bootstrap(final User user) {
        serviceWallet.createDefaultWallet(user);
        subscriptionService.createFreeSubscription(user);
    }
}
