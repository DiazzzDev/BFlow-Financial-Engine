package bflow.recurring.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.entity.RecurringTransaction;
import bflow.wallet.DTO.UpcomingTransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps recurring transaction entities to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface RecurringMapper {

    /** Maps nested wallet and category identifiers. */
    @Mapping(target = "walletId", source = "wallet.id")
    @Mapping(target = "categoryId", source = "category.id")
    RecurringResponse toResponse(RecurringTransaction transaction);

    /** Maps a recurring transaction into the wallet upcoming summary. */
    UpcomingTransactionResponse toUpcomingResponse(
            RecurringTransaction transaction
    );
}
