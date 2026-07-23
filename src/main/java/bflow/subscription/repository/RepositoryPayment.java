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

    /**
     * Find a payment by its provider transaction identifier.
     *
     * @param providerPaymentId the provider payment identifier
     * @return the matching payment if present
     */
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    /**
     * Find a payment by the local reference value.
     *
     * @param reference the local payment reference
     * @return the matching payment if present
     */
    Optional<Payment> findByReference(String reference);

    /**
     * Find a payment by its idempotency key.
     *
     * @param idempotencyKey the idempotency key
     * @return the matching payment if present
     */
    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    /**
     * Check whether a payment with the idempotency key already exists.
     *
     * @param idempotencyKey the idempotency key
     * @return true when a matching payment exists
     */
    boolean existsByIdempotencyKey(UUID idempotencyKey);

    /**
     * Find all payments for a subscription.
     *
     * @param subscriptionId the subscription identifier
     * @return payments associated with the subscription
     */
    List<Payment> findBySubscriptionId(UUID subscriptionId);

    /**
     * Find payments by their current status.
     *
     * @param status the payment status
     * @return matching payments
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Check whether a payment already exists for a provider transaction.
     *
     * @param providerPaymentId the provider payment identifier
     * @return true when a matching payment exists
     */
    boolean existsByProviderPaymentId(String providerPaymentId);
}

