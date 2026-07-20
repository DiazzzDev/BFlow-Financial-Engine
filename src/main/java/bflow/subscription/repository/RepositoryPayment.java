package bflow.subscription.repository;

import bflow.subscription.entities.Payment;
import bflow.subscription.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryPayment extends JpaRepository<Payment, UUID> {

    /**
     * Find all payments for a given user.
     *
     * @param userId the user identifier
     * @return list of payments for the user
     */
    List<Payment> findByUserId(UUID userId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Optional<Payment> findByReference(String reference);

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByIdempotencyKey(UUID idempotencyKey);

    List<Payment> findBySubscriptionId(UUID subscriptionId);

    List<Payment> findByStatus(PaymentStatus status);

    boolean existsByProviderPaymentId(String providerPaymentId);
}
