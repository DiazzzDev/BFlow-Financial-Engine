package Diaz.Dev.BFlow.wallet.mapper;

import bflow.auth.entities.User;
import bflow.wallet.DTO.WalletInvitationResponse;
import bflow.wallet.DTO.WalletInvitationSentResponse;
import bflow.wallet.DTO.WalletMemberResponse;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletInvitation;
import bflow.wallet.entities.WalletUser;
import bflow.wallet.enums.WalletInvitationStatus;
import bflow.wallet.enums.WalletRole;
import bflow.wallet.mapper.WalletSharingMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WalletSharingMapperTest {

    private final WalletSharingMapper mapper =
            Mappers.getMapper(WalletSharingMapper.class);

    @Test
    void mapsIncomingAndOutgoingInvitationsWithNestedProfiles() {
        UUID walletId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-03-08T10:00:00Z");
        Instant respondedAt = Instant.parse("2026-03-02T10:00:00Z");
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setName("Shared wallet");
        User inviter = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .name("Owner")
                .pictureUrl("https://example.com/owner.png")
                .build();
        User invited = User.builder()
                .id(invitedUserId)
                .name("Member")
                .build();
        WalletInvitation invitation = new WalletInvitation();
        invitation.setId(invitationId);
        invitation.setWallet(wallet);
        invitation.setInvitedByUser(inviter);
        invitation.setInvitedUser(invited);
        invitation.setInvitedEmail("member@example.com");
        invitation.setStatus(WalletInvitationStatus.ACCEPTED);
        invitation.setCreatedAt(createdAt);
        invitation.setExpiresAt(expiresAt);
        invitation.setRespondedAt(respondedAt);

        WalletInvitationResponse incoming =
                mapper.toInvitationResponse(invitation);
        WalletInvitationSentResponse outgoing =
                mapper.toSentInvitationResponse(invitation);

        assertEquals(invitationId, incoming.getId());
        assertEquals(walletId, incoming.getWalletId());
        assertEquals("Shared wallet", incoming.getWalletName());
        assertEquals("Owner", incoming.getInvitedByName());
        assertEquals("owner@example.com", incoming.getInvitedByEmail());
        assertEquals("https://example.com/owner.png",
                incoming.getInvitedByPictureUrl());
        assertEquals(WalletInvitationStatus.ACCEPTED, incoming.getStatus());
        assertEquals(invitedUserId, outgoing.getInvitedUserId());
        assertEquals("Member", outgoing.getInvitedUserName());
        assertEquals(createdAt, outgoing.getCreatedAt());
        assertEquals(respondedAt, outgoing.getRespondedAt());
    }

    @Test
    void mapsWalletMemberAndReturnsNullForNullEntities() {
        UUID userId = UUID.randomUUID();
        Instant joinedAt = Instant.parse("2026-03-01T10:00:00Z");
        User member = User.builder()
                .id(userId)
                .email("member@example.com")
                .name("Member")
                .pictureUrl("https://example.com/member.png")
                .build();
        WalletUser walletUser = new WalletUser();
        walletUser.setUser(member);
        walletUser.setRole(WalletRole.MEMBER);
        walletUser.setCreatedAt(joinedAt);

        WalletMemberResponse response = mapper.toMemberResponse(walletUser);

        assertEquals(userId, response.getId());
        assertEquals("member@example.com", response.getEmail());
        assertEquals("Member", response.getName());
        assertEquals("https://example.com/member.png",
                response.getPictureUrl());
        assertEquals(WalletRole.MEMBER, response.getRole());
        assertEquals(joinedAt, response.getJoinedAt());
        assertNull(mapper.toInvitationResponse(null));
        assertNull(mapper.toSentInvitationResponse(null));
        assertNull(mapper.toMemberResponse(null));
    }

    @Test
    void mapsInvitationsAndMembershipsWithMissingRelatedEntities() {
        WalletInvitation invitation = new WalletInvitation();
        WalletInvitationResponse incoming =
                mapper.toInvitationResponse(invitation);
        WalletInvitationSentResponse outgoing =
                mapper.toSentInvitationResponse(invitation);
        WalletMemberResponse member =
                mapper.toMemberResponse(new WalletUser());

        assertNull(incoming.getWalletId());
        assertNull(incoming.getWalletName());
        assertNull(incoming.getInvitedByName());
        assertNull(incoming.getInvitedByEmail());
        assertNull(incoming.getInvitedByPictureUrl());
        assertNull(outgoing.getWalletId());
        assertNull(outgoing.getWalletName());
        assertNull(outgoing.getInvitedUserId());
        assertNull(outgoing.getInvitedUserName());
        assertNull(member.getId());
        assertNull(member.getEmail());
        assertNull(member.getName());
        assertNull(member.getPictureUrl());
    }
}
