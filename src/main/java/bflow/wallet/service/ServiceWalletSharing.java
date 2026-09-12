package bflow.wallet.service;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.repository.specs.UserSpecs;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.exception.ConflictException;
import bflow.common.exception.NotFoundException;
import bflow.common.exception.PlanLimitExceededException;
import bflow.common.i18n.MessageService;
import bflow.subscription.FeatureCodes;
import bflow.subscription.services.PlanLimitService;
import bflow.wallet.DTO.CollaboratorSearchResult;
import bflow.wallet.DTO.WalletInvitationResponse;
import bflow.wallet.DTO.WalletInvitationSentResponse;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.entities.WalletInvitation;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.CollaboratorStatus;
import bflow.wallet.enums.WalletInvitationStatus;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.repository.RepositoryWalletInvitation;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceWalletSharing {

    /**
     * Invitation expiration period.
     */
    private static final Duration INVITATION_EXPIRATION = Duration.ofDays(7);

    /**
     * Repository for wallet members.
     */
    private final RepositoryWalletUser repositoryWalletUser;

    /**
     * Repository for wallet invitations.
     */
    private final RepositoryWalletInvitation repositoryWalletInvitation;

    /**
     * Repository for application users.
     */
    private final RepositoryUser repositoryUser;

    /**
     * Service responsible for enforcing subscription plan limits.
     */
    private final PlanLimitService planLimitService;

    /**
     * Service responsible for sending email notifications.
     */
    private final EmailTemplateService emailTemplateService;

    /**
     * Service responsible for wallet operations.
     */
    private final ServiceWallet serviceWallet;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Maximum number of matches returned by the collaborator search,
     * mirroring a typeahead picker (GitHub's "Add people" dialog
     * shows a similarly small, scrollable list).
     */
    private static final int SEARCH_RESULT_LIMIT = 8;

    /**
     * Searches for users that can be invited to a wallet, matching
     * by name or email — the same lookup GitHub's "Add people to
     * repository" dialog performs. Only the wallet owner can search,
     * since only the owner can send invitations.
     *
     * @param walletId the wallet UUID
     * @param requesterId the user performing the search
     * @param query the search text (name or email fragment)
     * @return up to {@value #SEARCH_RESULT_LIMIT} matching users,
     *         flagged with their invitation status for this wallet
     */
    @Transactional(readOnly = true)
    public List<CollaboratorSearchResult> searchCollaborators(
            final UUID walletId,
            final UUID requesterId,
            final String query
    ) {

        WalletUser owner = validateOwner(walletId, requesterId);

        if (query == null || query.isBlank()) {
            return List.of();
        }

        Specification<User> spec = UserSpecs
                .nameOrEmailContains(query)
                .and(UserSpecs.isActive())
                .and(UserSpecs.excludeUser(requesterId));

        List<User> matches = repositoryUser.findAll(
                spec,
                PageRequest.of(0, SEARCH_RESULT_LIMIT)
        ).getContent();

        return toCollaboratorSearchResults(owner.getWallet().getId(), matches);
    }

    private List<CollaboratorSearchResult> toCollaboratorSearchResults(
            final UUID walletId,
            final List<User> matches
    ) {

        if (matches.isEmpty()) {
            return List.of();
        }

        Set<String> memberEmails = new HashSet<>(
                repositoryWalletUser.findMemberEmailsByWalletId(walletId)
        );

        Set<String> pendingEmails = new HashSet<>(
                repositoryWalletInvitation.findInvitedEmailsByWalletIdAndStatus(
                        walletId,
                        WalletInvitationStatus.PENDING
                )
        );

        return matches.stream()
                .map(user -> new CollaboratorSearchResult(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPictureUrl(),
                    resolveStatus(user.getEmail(), memberEmails, pendingEmails)
                ))
                .toList();
    }

    private CollaboratorStatus resolveStatus(
            final String email,
            final Set<String> memberEmails,
            final Set<String> pendingEmails
    ) {

        if (memberEmails.contains(email)) {
            return CollaboratorStatus.ALREADY_MEMBER;
        }

        if (pendingEmails.contains(email)) {
            return CollaboratorStatus.INVITATION_PENDING;
        }

        return CollaboratorStatus.INVITABLE;
    }

    /**
     * Creates and sends a wallet invitation to the specified email address.
     *
     * @param walletId the wallet UUID
     * @param inviterUserId the owner sending the invitation
     * @param invitedEmail the email address of the invited user
     * @return the created invitation
     */
    public WalletInvitationResponse inviteMember(
            final UUID walletId,
            final UUID inviterUserId,
            final String invitedEmail
    ) {

        WalletUser owner = validateOwner(walletId, inviterUserId);

        validateInvitationPermissions(owner);

        String normalizedEmail = invitedEmail
                .trim()
                .toLowerCase(Locale.ROOT);

        validateInvitedEmail(owner, normalizedEmail);

        WalletInvitation invitation =
                createInvitation(owner, normalizedEmail);

        sendInvitation(invitation);

        return toResponse(invitation);
    }

    private WalletUser validateOwner(
            final UUID walletId,
            final UUID userId
    ) {

        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                messageService.get("wallet.accessDenied")
                        ));

        if (walletUser.getRole() != WalletRole.OWNER) {
            throw new AccessDeniedException(
                    messageService.get("wallet.owner.onlyAction")
            );
        }

        return walletUser;
    }

    private void validateInvitationPermissions(
            final WalletUser owner
    ) {

        UUID ownerId = owner.getUser().getId();

        planLimitService.assertFeatureEnabled(
                ownerId,
                FeatureCodes.CAN_CREATE_SHARED_WALLETS
        );

        long occupiedSeats =
                repositoryWalletUser.countByWalletId(
                        owner.getWallet().getId()
                )
                        +
                        repositoryWalletInvitation.countByWalletIdAndStatus(
                                owner.getWallet().getId(),
                                WalletInvitationStatus.PENDING
                        );

        planLimitService.assertCanCreate(
                ownerId,
                FeatureCodes.WALLET_MEMBERS,
                occupiedSeats
        );
    }

    private void validateInvitedEmail(
            final WalletUser owner,
            final String invitedEmail
    ) {

        String normalizedEmail =
                invitedEmail.trim().toLowerCase();

        if (owner.getUser().getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new ConflictException(
                    messageService.get("wallet.invitation.selfInvite")
            );
        }

        if (repositoryWalletUser.existsByWalletIdAndUserEmail(
                owner.getWallet().getId(),
                normalizedEmail
        )) {

            throw new ConflictException(
                    messageService.get("wallet.invitation.alreadyMember")
            );
        }

        if (repositoryWalletInvitation
                .existsByWalletIdAndInvitedEmailAndStatus(
                        owner.getWallet().getId(),
                        normalizedEmail,
                        WalletInvitationStatus.PENDING
                )) {

            throw new ConflictException(
                    messageService.get("wallet.invitation.alreadyPending")
            );
        }
    }

    private WalletInvitation createInvitation(
            final WalletUser owner,
            final String invitedEmail
    ) {

        WalletInvitation invitation =
                new WalletInvitation();

        invitation.setWallet(owner.getWallet());

        invitation.setInvitedByUser(owner.getUser());

        invitation.setInvitedEmail(
                invitedEmail.trim().toLowerCase()
        );

        repositoryUser.findByEmail(invitedEmail.trim().toLowerCase())
                .ifPresent(invitation::setInvitedUser);

        invitation.setStatus(
                WalletInvitationStatus.PENDING
        );

        invitation.setToken(
                UUID.randomUUID().toString()
        );

        invitation.setExpiresAt(
                Instant.now().plus(INVITATION_EXPIRATION)
        );

        return repositoryWalletInvitation.save(invitation);
    }

    private void sendInvitation(
            final WalletInvitation invitation
    ) {
        emailTemplateService.sendWalletInvitationEmail(
                invitation.getInvitedEmail(),
                invitation.getInvitedByUser().getEmail(),
                invitation.getWallet().getName(),
                invitation.getToken(),
                invitation.getExpiresAt(),
                bflow.auth.enums.SupportedLanguage.ES
        );
    }

    /**
     * Accepts a pending wallet invitation, identified by the token
     * sent in the invitation email. Used by the email deep-link flow.
     *
     * @param token the invitation token
     * @param acceptingUserId the user accepting the invitation
     * @return the updated wallet information
     */
    public WalletResponse acceptInvitation(
            final String token,
            final UUID acceptingUserId
    ) {

        WalletInvitation invitation = repositoryWalletInvitation
                .findByToken(token)
                .orElseThrow(() ->
                        new NotFoundException(
                                messageService.get("wallet.invitation.notFound")
                        ));

        return doAcceptInvitation(invitation, acceptingUserId);
    }

    /**
     * Accepts a pending wallet invitation, identified by its id.
     * Used by the in-app "pending invitations" list, where the token
     * (only ever delivered by email) isn't available.
     *
     * @param invitationId the invitation UUID
     * @param acceptingUserId the user accepting the invitation
     * @return the updated wallet information
     */
    public WalletResponse acceptInvitationById(
            final UUID invitationId,
            final UUID acceptingUserId
    ) {

        WalletInvitation invitation = repositoryWalletInvitation
                .findById(invitationId)
                .orElseThrow(() ->
                        new NotFoundException(
                                messageService.get("wallet.invitation.notFound")
                        ));

        return doAcceptInvitation(invitation, acceptingUserId);
    }

    private WalletResponse doAcceptInvitation(
            final WalletInvitation invitation,
            final UUID acceptingUserId
    ) {

        validatePendingInvitation(invitation);

        User user = repositoryUser.findById(acceptingUserId)
                .orElseThrow(() ->
                        new NotFoundException(messageService.get("user.notFound")));

        validateInvitationRecipient(invitation, user);

        validateInvitationStillFits(invitation);

        if (repositoryWalletUser.existsByWalletIdAndUserId(
                invitation.getWallet().getId(),
                acceptingUserId
        )) {
            throw new ConflictException(
                    messageService.get("wallet.invitation.alreadyMemberSelf")
            );
        }

        WalletUser walletUser = new WalletUser();
        walletUser.setWallet(invitation.getWallet());
        walletUser.setUser(user);
        walletUser.setRole(WalletRole.MEMBER);

        repositoryWalletUser.save(walletUser);

        invitation.setStatus(WalletInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());

        repositoryWalletInvitation.save(invitation);

        return serviceWallet.convertToDTO(walletUser);
    }

    private WalletInvitationResponse toResponse(
            final WalletInvitation invitation
    ) {
        return new WalletInvitationResponse(
                invitation.getId(),
                invitation.getWallet().getId(),
                invitation.getWallet().getName(),
                invitation.getInvitedEmail(),
                invitation.getInvitedByUser().getName(),
                invitation.getInvitedByUser().getEmail(),
                invitation.getInvitedByUser().getPictureUrl(),
                invitation.getStatus(),
                invitation.getExpiresAt()
        );
    }

    private void validatePendingInvitation(
            final WalletInvitation invitation
    ) {

        if (invitation.getStatus() != WalletInvitationStatus.PENDING) {
            throw new ConflictException(
                    messageService.get("wallet.invitation.notAvailable")
            );
        }

        if (invitation.isExpired()) {

            invitation.setStatus(WalletInvitationStatus.EXPIRED);
            invitation.setRespondedAt(Instant.now());

            repositoryWalletInvitation.save(invitation);

            throw new ConflictException(
                    messageService.get("wallet.invitation.expired")
            );
        }
    }

    private void validateInvitationRecipient(
            final WalletInvitation invitation,
            final User user
    ) {

        if (!invitation.getInvitedEmail()
                .equalsIgnoreCase(user.getEmail())) {

            throw new AccessDeniedException(
                    messageService.get("wallet.invitation.wrongEmail")
            );
        }
    }

    private void validateInvitationStillFits(
            final WalletInvitation invitation
    ) {

        UUID ownerId = invitation
                .getInvitedByUser()
                .getId();

        long occupiedSeats =
                repositoryWalletUser.countByWalletId(
                        invitation.getWallet().getId()
                )
                        +
                        repositoryWalletInvitation.countByWalletIdAndStatus(
                                invitation.getWallet().getId(),
                                WalletInvitationStatus.PENDING
                        );

        try {

            planLimitService.assertCanCreate(
                    ownerId,
                    FeatureCodes.WALLET_MEMBERS,
                    occupiedSeats - 1
            );

        } catch (PlanLimitExceededException ex) {

            invitation.setStatus(WalletInvitationStatus.REJECTED);
            invitation.setRespondedAt(Instant.now());

            repositoryWalletInvitation.save(invitation);

            throw new ConflictException(
                    messageService.get("wallet.invitation.noSlots")
            );
        }
    }

    /**
     * Rejects a pending wallet invitation, identified by the token
     * sent in the invitation email. Used by the email deep-link flow.
     *
     * @param token the invitation token
     * @param rejectingUserId the user rejecting the invitation
     */
    public void rejectInvitation(
            final String token,
            final UUID rejectingUserId
    ) {

        WalletInvitation invitation = repositoryWalletInvitation
                .findByToken(token)
                .orElseThrow(() ->
                        new NotFoundException(
                                messageService.get("wallet.invitation.notFound")
                        ));

        doRejectInvitation(invitation, rejectingUserId);
    }

    /**
     * Rejects a pending wallet invitation, identified by its id.
     * Used by the in-app "pending invitations" list, where the token
     * (only ever delivered by email) isn't available.
     *
     * @param invitationId the invitation UUID
     * @param rejectingUserId the user rejecting the invitation
     */
    public void rejectInvitationById(
            final UUID invitationId,
            final UUID rejectingUserId
    ) {

        WalletInvitation invitation = repositoryWalletInvitation
                .findById(invitationId)
                .orElseThrow(() ->
                        new NotFoundException(
                                messageService.get("wallet.invitation.notFound")
                        ));

        doRejectInvitation(invitation, rejectingUserId);
    }

    private void doRejectInvitation(
            final WalletInvitation invitation,
            final UUID rejectingUserId
    ) {

        validatePendingInvitation(invitation);

        User user = repositoryUser.findById(rejectingUserId)
                .orElseThrow(() ->
                        new NotFoundException(messageService.get("user.notFound")));

        validateInvitationRecipient(invitation, user);

        invitation.setStatus(WalletInvitationStatus.REJECTED);
        invitation.setRespondedAt(Instant.now());

        repositoryWalletInvitation.save(invitation);
    }

    /**
     * Cancels a pending wallet invitation.
     *
     * @param invitationId the invitation UUID
     * @param ownerUserId the wallet owner's UUID
     */
    public void cancelInvitation(
            final UUID invitationId,
            final UUID ownerUserId
    ) {

        WalletInvitation invitation = repositoryWalletInvitation
                .findById(invitationId)
                .orElseThrow(() ->
                        new NotFoundException(
                                messageService.get("wallet.invitation.notFound")
                        ));

        validateOwner(
                invitation.getWallet().getId(),
                ownerUserId
        );

        if (invitation.getStatus() != WalletInvitationStatus.PENDING) {
            throw new ConflictException(
                    messageService.get("wallet.invitation.onlyPendingCanCancel")
            );
        }

        invitation.setStatus(WalletInvitationStatus.CANCELED);
        invitation.setRespondedAt(Instant.now());

        repositoryWalletInvitation.save(invitation);
    }

    /**
     * Removes a member from a shared wallet.
     *
     * @param walletId the wallet UUID
     * @param ownerUserId the wallet owner's UUID
     * @param memberUserId the member UUID to remove
     */
    public void removeMember(
            final UUID walletId,
            final UUID ownerUserId,
            final UUID memberUserId
    ) {

        validateOwner(walletId, ownerUserId);

        WalletUser member = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, memberUserId)
                .orElseThrow(() ->
                        new NotFoundException(
                                messageService.get("wallet.member.notFound")
                        ));

        if (member.getRole() == WalletRole.OWNER) {
            throw new ConflictException(
                    messageService.get("wallet.owner.cannotBeRemoved")
            );
        }

        repositoryWalletUser.delete(member);
    }

    /**
     * Leaves a shared wallet — the self-service counterpart to
     * {@link #removeMember}, which only the owner can invoke on
     * someone else.
     *
     * The owner cannot leave through this method: a wallet with no
     * owner would leave every remaining member without anyone able
     * to invite, remove members, or otherwise administer it.
     *
     * @param walletId the wallet UUID
     * @param userId the user leaving the wallet
     */
    public void leaveWallet(
            final UUID walletId,
            final UUID userId
    ) {

        WalletUser member = repositoryWalletUser
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() ->
                        new NotFoundException(
                                messageService.get("wallet.member.notAMember")
                        ));

        if (member.getRole() == WalletRole.OWNER) {
            throw new ConflictException(
                    messageService.get("wallet.owner.cannotLeave")
            );
        }

        repositoryWalletUser.delete(member);
    }

    /**
     * Retrieves all pending wallet invitations for the specified user.
     *
     * Only invitations that have not expired are returned.
     *
     * @param userId the user identifier
     * @return a list of pending wallet invitations
     * @throws NotFoundException if the user does not exist
     */
    public List<WalletInvitationResponse> getPendingInvitations(
            final UUID userId
    ) {

        User user = repositoryUser.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException(messageService.get("user.notFound")));

        return repositoryWalletInvitation
                .findByInvitedEmailAndStatus(
                        user.getEmail(),
                        WalletInvitationStatus.PENDING
                )
                .stream()
                .filter(invitation -> !invitation.isExpired())
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves every invitation the specified user has sent for a
     * specific wallet, regardless of status — so pending ones still
     * awaiting a response are included alongside accepted, rejected,
     * expired, and canceled ones.
     *
     * @param walletId the wallet identifier to filter invitations by
     * @param userId the sending user's identifier
     * @return the user's sent-invitation history for that wallet,
     *         newest first
     */
    public List<WalletInvitationSentResponse> getSentInvitations(
            final UUID walletId,
            final UUID userId
    ) {

        validateOwner(walletId, userId);

        return repositoryWalletInvitation
                .findByInvitedByUserIdAndWalletIdOrderByCreatedAtDesc(
                        userId,
                        walletId
                )
                .stream()
                .map(this::toSentResponse)
                .toList();
    }

    private WalletInvitationSentResponse toSentResponse(
            final WalletInvitation invitation
    ) {

        User invitedUser = invitation.getInvitedUser();

        return new WalletInvitationSentResponse(
                invitation.getId(),
                invitation.getWallet().getId(),
                invitation.getWallet().getName(),
                invitation.getInvitedEmail(),
                invitedUser != null ? invitedUser.getId() : null,
                invitedUser != null ? invitedUser.getName() : null,
                invitation.getStatus(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getRespondedAt()
        );
    }
}
