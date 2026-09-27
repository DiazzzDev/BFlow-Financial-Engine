package bflow.receipts.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.receipts.DTO.ReceiptUploadResponse;
import bflow.receipts.entity.ReceiptUpload;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps receipt upload entities to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface ReceiptUploadMapper {

    /** Maps a receipt upload and its related identifiers to a response. */
    @Mapping(target = "fileId", source = "storedFile.id")
    @Mapping(target = "walletId", source = "wallet.id")
    @Mapping(target = "resultingExpenseId",
            source = "resultingTransactionId")
    ReceiptUploadResponse toResponse(ReceiptUpload receipt);
}
