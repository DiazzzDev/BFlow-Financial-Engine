package bflow.wallet.scheduler;

import bflow.wallet.enums.WalletInvitationStatus;
import bflow.wallet.repository.RepositoryWalletInvitation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Bulk-transitions PENDING wallet invitations past their expiration
 * date to EXPIRED.
 *
 * <p>Invitation status otherwise only advances lazily, when someone
 * tries to accept or reject an invitation after the fact (see
 * {@code ServiceWalletSharing#validatePendingInvitation}). An
 * invitation nobody ever touched stays PENDING in the database
 * forever, even though it's already well past its expiration date.
 * That stale PENDING status is read literally — not
 * expiration-aware — by two places:
 *
 * <ul>
 *   <li>the wallet member seat-limit check, which counts current
 *       members plus PENDING invitations as "occupied seats", so a
 *       long-expired invitation can keep counting against the
 *       plan's member limit indefinitely;</li>
 *   <li>the duplicate-pending-invite check, which blocks re-inviting
 *       an email that already has a PENDING row, even if that
 *       invitation expired weeks ago.</li>
 * </ul>
 *
 * <p>Running this every 15 minutes keeps that staleness window small
 * relative to the 7-day expiration period, without needing every
 * caller of "is this invitation still pending" to independently
 * account for expiration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletInvitationCleanupTask {

    /**
     * Repository used to bulk-expire overdue invitations.
     */
    private final RepositoryWalletInvitation repositoryWalletInvitation;

    /**
     * Transitions every PENDING invitation whose expiration date has
     * passed to EXPIRED, and logs how many were updated.
     */
    @Scheduled(cron = "0 */12 * * * *")
    @SchedulerLock(name = "walletInvitationCleanup")
    @Transactional
    public void expireOverdueInvitations() {
        int expired = repositoryWalletInvitation.expireOverdueInvitations(
                WalletInvitationStatus.PENDING,
                WalletInvitationStatus.EXPIRED,
                Instant.now()
        );

        if (expired > 0) {
            log.info(
                    "Wallet invitation cleanup: {} invitation(s) expired",
                    expired
            );
        }
    }
}
