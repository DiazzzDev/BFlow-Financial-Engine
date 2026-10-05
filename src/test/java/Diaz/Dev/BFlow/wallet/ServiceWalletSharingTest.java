package Diaz.Dev.BFlow.wallet;

import bflow.auth.entities.User;
import bflow.auth.enums.SupportedLanguage;
import bflow.auth.repository.RepositoryUser;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.i18n.MessageService;
import bflow.notifications.service.NotificationService;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletInvitation;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.WalletInvitationStatus;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletInvitation;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;
import bflow.wallet.service.ServiceWalletSharing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests delivery details for wallet collaboration invitations. */
@ExtendWith(MockitoExtension.class)
class ServiceWalletSharingTest {

    @Mock private RepositoryWalletUser repositoryWalletUser;
    @Mock private RepositoryWalletInvitation repositoryWalletInvitation;
    @Mock private RepositoryUser repositoryUser;
    @Mock private PlanLimitService planLimitService;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private ServiceWallet serviceWallet;
    @Mock private MessageService messageService;
    @Mock private NotificationService notificationService;

    @InjectMocks private ServiceWalletSharing serviceWalletSharing;

    @Test
    void inviteMember_blankInviterNameUsesEmailForEmailAndNotification() {
        UUID walletId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        User inviter = new User();
        inviter.setId(inviterId);
        inviter.setName("  ");
        inviter.setEmail("owner@example.com");
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Household");
        WalletUser owner = new WalletUser();
        owner.setUser(inviter);
        owner.setWallet(wallet);
        owner.setRole(WalletRole.OWNER);

        User invited = new User();
        invited.setId(UUID.randomUUID());
        invited.setLanguage(SupportedLanguage.EN);
        when(repositoryWalletUser.findByWalletIdAndUserId(walletId, inviterId))
                .thenReturn(Optional.of(owner));
        when(repositoryWalletUser.countByWalletId(walletId)).thenReturn(1L);
        when(repositoryUser.findByEmail("guest@example.com"))
                .thenReturn(Optional.of(invited));
        when(repositoryWalletInvitation.save(any(WalletInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        serviceWalletSharing.inviteMember(
                walletId, inviterId, "GUEST@example.com");

        verify(emailTemplateService).sendWalletInvitationEmail(
                eq("guest@example.com"), eq("owner@example.com"),
                eq("Household"), any(String.class), any(),
                eq(SupportedLanguage.EN));
        ArgumentCaptor<UUID> invitationId = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService).sendWalletInvitation(
                eq(invited), invitationId.capture(), eq("owner@example.com"),
                eq("Household"));
        ArgumentCaptor<WalletInvitation> invitation =
                ArgumentCaptor.forClass(WalletInvitation.class);
        verify(repositoryWalletInvitation).save(invitation.capture());
        assertEquals(WalletInvitationStatus.PENDING,
                invitation.getValue().getStatus());
    }
}
