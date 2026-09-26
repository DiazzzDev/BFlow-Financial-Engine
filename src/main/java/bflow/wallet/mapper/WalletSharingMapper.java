package bflow.wallet.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.wallet.DTO.WalletInvitationResponse;
import bflow.wallet.DTO.WalletInvitationSentResponse;
import bflow.wallet.DTO.WalletMemberResponse;
import bflow.wallet.entities.WalletInvitation;
import bflow.wallet.entities.WalletUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps wallet sharing entities to their API response DTOs. */
@Mapper(config = BaseMapperConfig.class)
public interface WalletSharingMapper {

    /** Maps an incoming invitation and its inviter profile. */
    @Mapping(target = "walletId", source = "wallet.id")
    @Mapping(target = "walletName", source = "wallet.name")
    @Mapping(target = "invitedByName", source = "invitedByUser.name")
    @Mapping(target = "invitedByEmail", source = "invitedByUser.email")
    @Mapping(target = "invitedByPictureUrl",
            source = "invitedByUser.pictureUrl")
    WalletInvitationResponse toInvitationResponse(
            WalletInvitation invitation
    );

    /** Maps an outgoing invitation and optional invited account. */
    @Mapping(target = "walletId", source = "wallet.id")
    @Mapping(target = "walletName", source = "wallet.name")
    @Mapping(target = "invitedUserId", source = "invitedUser.id")
    @Mapping(target = "invitedUserName", source = "invitedUser.name")
    WalletInvitationSentResponse toSentInvitationResponse(
            WalletInvitation invitation
    );

    /** Maps a wallet membership and its user profile. */
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "pictureUrl", source = "user.pictureUrl")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "joinedAt", source = "createdAt")
    WalletMemberResponse toMemberResponse(WalletUser walletUser);
}
