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

    private final ServiceWallet serviceWallet;
    private final SubscriptionService subscriptionService;

    @Override
    public void bootstrap(User user) {
        serviceWallet.createDefaultWallet(user);
        subscriptionService.createFreeSubscription(user);
    }
}