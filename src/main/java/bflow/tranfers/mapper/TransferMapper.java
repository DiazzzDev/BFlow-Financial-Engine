package bflow.tranfers.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.tranfers.DTO.TransferenceResponse;
import bflow.tranfers.entities.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps transfer entities to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface TransferMapper {

    /**
     * Maps both wallet relationships and the transfer status.
     *
     * @param transfer transfer entity
     * @return transfer response
     */
    @Mapping(target = "fromWalletId", source = "fromWallet.id")
    @Mapping(target = "fromWalletName", source = "fromWallet.name")
    @Mapping(target = "toWalletId", source = "toWallet.id")
    @Mapping(target = "toWalletName", source = "toWallet.name")
    TransferenceResponse toResponse(Transfer transfer);
}
