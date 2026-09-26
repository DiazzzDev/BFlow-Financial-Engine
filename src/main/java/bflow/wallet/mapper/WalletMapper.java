package bflow.wallet.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.wallet.DTO.WalletResponse;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps wallet data and the current user's membership to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface WalletMapper {

    /**
     * Maps a wallet and its membership metadata to a response.
     *
     * @param wallet wallet entity
     * @param walletUser current user's wallet membership
     * @param memberCount number of wallet members
     * @return wallet response
     */
    @Mapping(target = "role", source = "walletUser.role")
    @Mapping(target = "memberCount", source = "memberCount")
    @Mapping(target = "id", source = "wallet.id")
    @Mapping(target = "createdAt", source = "wallet.createdAt")
    WalletResponse toResponse(
            Wallet wallet,
            WalletUser walletUser,
            Integer memberCount
    );
}
