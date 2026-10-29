package Diaz.Dev.BFlow.wallet;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.common.exception.ConflictException;
import bflow.common.exception.NotFoundException;
import bflow.common.i18n.MessageService;
import bflow.common.exception.PlanLimitExceededException;
import bflow.notifications.service.NotificationService;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletInvitation;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.CollaboratorStatus;
import bflow.wallet.enums.WalletInvitationStatus;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletInvitation;
import bflow.wallet.repository.RepositoryWalletUser;
import bflow.wallet.service.ServiceWallet;
import bflow.wallet.service.ServiceWalletSharing;
import bflow.common.aws.service.EmailTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceWalletSharingLifecycleTest {

    @Mock private RepositoryWalletUser walletUserRepository;
    @Mock private RepositoryWalletInvitation invitationRepository;
    @Mock private RepositoryUser userRepository;
    @Mock private PlanLimitService planLimitService;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private ServiceWallet serviceWallet;
    @Mock private MessageService messageService;
    @Mock private NotificationService notificationService;

    private ServiceWalletSharing service;
    private UUID walletId;
    private UUID ownerId;
    private UUID guestId;
    private Wallet wallet;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ServiceWalletSharing(walletUserRepository,
                invitationRepository, userRepository, planLimitService,
                emailTemplateService, serviceWallet, messageService,
                notificationService);
        walletId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        guestId = UUID.randomUUID();
        wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Household");
        owner = new User();
        owner.setId(ownerId);
        owner.setName("Owner");
        owner.setEmail("owner@example.com");
    }

    @Test
    void collaboratorSearchBlankQueryReturnsNoCandidates() {
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink()));

        assertThat(service.searchCollaborators(walletId, ownerId, "  "))
                .isEmpty();
        verify(userRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<User>>any(),
                any(PageRequest.class));
    }

    @Test
    void collaboratorSearchLabelsExistingPendingAndInvitableCandidates() {
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink()));
        User member = user("member@example.com", "Member");
        User pending = user("pending@example.com", "Pending");
        User newUser = user("new@example.com", "New");
        when(userRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<User>>any(),
                any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(member, pending, newUser)));
        when(walletUserRepository.findMemberEmailsByWalletId(walletId))
                .thenReturn(List.of(member.getEmail()));
        when(invitationRepository.findInvitedEmailsByWalletIdAndStatus(
                walletId, WalletInvitationStatus.PENDING))
                .thenReturn(List.of(pending.getEmail()));

        var result = service.searchCollaborators(walletId, ownerId, "@example.com");

        assertThat(result).extracting("status").containsExactly(
                CollaboratorStatus.ALREADY_MEMBER,
                CollaboratorStatus.INVITATION_PENDING,
                CollaboratorStatus.INVITABLE);
        verify(userRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<User>>any(),
                eq(PageRequest.of(0, 8)));
    }

    @Test
    void collaboratorSearchWithNoMatchesSkipsMembershipLookups() {
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink()));
        when(userRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<User>>any(),
                any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.searchCollaborators(walletId, ownerId, "nobody"))
                .isEmpty();
        verify(walletUserRepository, never()).findMemberEmailsByWalletId(any());
        verify(invitationRepository, never())
                .findInvitedEmailsByWalletIdAndStatus(any(), any());
    }

    @Test
    void collaboratorSearchRejectsNonOwner() {
        WalletUser member = ownerLink();
        member.setRole(WalletRole.MEMBER);
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.searchCollaborators(
                walletId, ownerId, "guest"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void inviteRejectsOwnerSelfInvite() {
        allowInvite();

        assertThatThrownBy(() -> service.inviteMember(
                walletId, ownerId, " OWNER@example.com "))
                .isInstanceOf(ConflictException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteRejectsEmailAlreadyInWallet() {
        allowInvite();
        when(walletUserRepository.existsByWalletIdAndUserEmail(
                walletId, "member@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(
                walletId, ownerId, "Member@example.com"))
                .isInstanceOf(ConflictException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteRejectsDuplicatePendingInvitation() {
        allowInvite();
        when(invitationRepository.existsByWalletIdAndInvitedEmailAndStatus(
                walletId, "pending@example.com", WalletInvitationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(
                walletId, ownerId, "pending@example.com"))
                .isInstanceOf(ConflictException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteStopsBeforeSeatQueriesWhenPlanFeatureIsUnavailable() {
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink()));
        org.mockito.Mockito.doThrow(new PlanLimitExceededException("disabled"))
                .when(planLimitService).assertFeatureEnabled(any(), anyString());

        assertThatThrownBy(() -> service.inviteMember(
                walletId, ownerId, "guest@example.com"))
                .isInstanceOf(PlanLimitExceededException.class);

        verify(walletUserRepository, never()).countByWalletId(any());
    }

    @Test
    void acceptRejectsInvitationAddressedToAnotherAccount() {
        WalletInvitation invitation = invitation("guest@example.com");
        when(invitationRepository.findByToken("valid-token"))
                .thenReturn(Optional.of(invitation));
        User wrongUser = user("other@example.com", "Other");
        when(userRepository.findById(guestId)).thenReturn(Optional.of(wrongUser));

        assertThatThrownBy(() -> service.acceptInvitation("valid-token", guestId))
                .isInstanceOf(AccessDeniedException.class);

        verify(walletUserRepository, never()).save(any());
        assertThat(invitation.getStatus()).isEqualTo(WalletInvitationStatus.PENDING);
    }

    @Test
    void acceptExpiredInvitationMarksItExpiredAndDoesNotAddMember() {
        WalletInvitation invitation = invitation("guest@example.com");
        invitation.setExpiresAt(Instant.now().minusSeconds(5));
        when(invitationRepository.findById(invitation.getId()))
                .thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvitationById(
                invitation.getId(), guestId))
                .isInstanceOf(ConflictException.class);

        assertThat(invitation.getStatus()).isEqualTo(WalletInvitationStatus.EXPIRED);
        verify(invitationRepository).save(invitation);
        verify(walletUserRepository, never()).save(any());
    }

    @Test
    void acceptByIdCreatesMemberAndMarksInvitationAccepted() {
        WalletInvitation invitation = invitation("guest@example.com");
        User guest = user("GUEST@example.com", "Guest");
        when(invitationRepository.findById(invitation.getId()))
                .thenReturn(Optional.of(invitation));
        when(userRepository.findById(guestId)).thenReturn(Optional.of(guest));
        when(walletUserRepository.countByWalletId(walletId)).thenReturn(2L);
        when(invitationRepository.countByWalletIdAndStatus(
                walletId, WalletInvitationStatus.PENDING)).thenReturn(1L);
        when(serviceWallet.convertToDTO(any(WalletUser.class)))
                .thenReturn(new WalletResponse());

        service.acceptInvitationById(invitation.getId(), guestId);

        ArgumentCaptor<WalletUser> member = ArgumentCaptor.forClass(WalletUser.class);
        verify(walletUserRepository).save(member.capture());
        assertThat(member.getValue().getRole()).isEqualTo(WalletRole.MEMBER);
        assertThat(invitation.getStatus()).isEqualTo(WalletInvitationStatus.ACCEPTED);
        verify(invitationRepository).save(invitation);
    }

    @Test
    void acceptInvitationWhenOwnerLostMemberSlotRejectsInvitation() {
        WalletInvitation invitation = invitation("guest@example.com");
        User guest = user("guest@example.com", "Guest");
        when(invitationRepository.findByToken("valid-token"))
                .thenReturn(Optional.of(invitation));
        when(userRepository.findById(guestId)).thenReturn(Optional.of(guest));
        when(walletUserRepository.countByWalletId(walletId)).thenReturn(3L);
        when(invitationRepository.countByWalletIdAndStatus(
                walletId, WalletInvitationStatus.PENDING)).thenReturn(1L);
        org.mockito.Mockito.doThrow(new PlanLimitExceededException("full"))
                .when(planLimitService).assertCanCreate(
                        ownerId, "WALLET_MEMBERS", 3L);

        assertThatThrownBy(() -> service.acceptInvitation("valid-token", guestId))
                .isInstanceOf(ConflictException.class);

        assertThat(invitation.getStatus()).isEqualTo(WalletInvitationStatus.REJECTED);
        verify(walletUserRepository, never()).save(any());
        verify(invitationRepository).save(invitation);
    }

    @Test
    void rejectInvitationPersistsRejectedStateForMatchingRecipient() {
        WalletInvitation invitation = invitation("guest@example.com");
        when(invitationRepository.findByToken("valid-token"))
                .thenReturn(Optional.of(invitation));
        when(userRepository.findById(guestId))
                .thenReturn(Optional.of(user("GUEST@example.com", "Guest")));

        service.rejectInvitation("valid-token", guestId);

        assertThat(invitation.getStatus()).isEqualTo(WalletInvitationStatus.REJECTED);
        assertThat(invitation.getRespondedAt()).isNotNull();
        verify(invitationRepository).save(invitation);
    }

    @Test
    void cancelInvitationRequiresOwnerAndPersistsCanceledState() {
        WalletInvitation invitation = invitation("guest@example.com");
        when(invitationRepository.findById(invitation.getId()))
                .thenReturn(Optional.of(invitation));
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink()));

        service.cancelInvitation(invitation.getId(), ownerId);

        assertThat(invitation.getStatus()).isEqualTo(WalletInvitationStatus.CANCELED);
        assertThat(invitation.getRespondedAt()).isNotNull();
        verify(invitationRepository).save(invitation);
    }

    @Test
    void removeMemberDeletesMemberButCannotRemoveWalletOwner() {
        WalletUser ownerLink = ownerLink();
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink));
        WalletUser member = new WalletUser();
        member.setRole(WalletRole.MEMBER);
        when(walletUserRepository.findByWalletIdAndUserId(walletId, guestId))
                .thenReturn(Optional.of(member));

        service.removeMember(walletId, ownerId, guestId);

        verify(walletUserRepository).delete(member);

        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink));
        assertThatThrownBy(() -> service.removeMember(walletId, ownerId, ownerId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void memberCanLeaveButOwnerCannotAbandonSharedWallet() {
        WalletUser member = new WalletUser();
        member.setRole(WalletRole.MEMBER);
        when(walletUserRepository.findByWalletIdAndUserId(walletId, guestId))
                .thenReturn(Optional.of(member));

        service.leaveWallet(walletId, guestId);
        verify(walletUserRepository).delete(member);

        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink()));
        assertThatThrownBy(() -> service.leaveWallet(walletId, ownerId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void invitationLookupForUnknownTokenReturnsNotFound() {
        when(invitationRepository.findByToken("missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvitation("missing", guestId))
                .isInstanceOf(NotFoundException.class);
    }

    private void allowInvite() {
        when(walletUserRepository.findByWalletIdAndUserId(walletId, ownerId))
                .thenReturn(Optional.of(ownerLink()));
        when(walletUserRepository.countByWalletId(walletId)).thenReturn(1L);
        when(invitationRepository.countByWalletIdAndStatus(
                walletId, WalletInvitationStatus.PENDING)).thenReturn(0L);
    }

    private WalletUser ownerLink() {
        WalletUser link = new WalletUser();
        link.setWallet(wallet);
        link.setUser(owner);
        link.setRole(WalletRole.OWNER);
        return link;
    }

    private User user(String email, String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setName(name);
        return user;
    }

    private WalletInvitation invitation(String email) {
        WalletInvitation invitation = new WalletInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setWallet(wallet);
        invitation.setInvitedByUser(owner);
        invitation.setInvitedEmail(email);
        invitation.setStatus(WalletInvitationStatus.PENDING);
        invitation.setToken("valid-token");
        invitation.setExpiresAt(Instant.now().plusSeconds(300));
        return invitation;
    }
}
