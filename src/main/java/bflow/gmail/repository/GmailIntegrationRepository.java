package bflow.gmail.repository;

import bflow.gmail.entity.GmailIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GmailIntegrationRepository extends JpaRepository<GmailIntegration, UUID> {
    Optional<GmailIntegration> findByUserIdAndStatus(UUID userId, String status);
    Optional<GmailIntegration> findByUserId(UUID userId);
    java.util.List<GmailIntegration> findAllByStatusAndWalletIdIsNotNull(String status);
}
