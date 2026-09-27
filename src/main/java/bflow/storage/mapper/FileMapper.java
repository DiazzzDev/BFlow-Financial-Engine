package bflow.storage.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.storage.DTO.FileResponse;
import bflow.storage.entity.StoredFile;
import org.mapstruct.Mapper;

/** Maps stored file entities to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface FileMapper {

    /** Maps a stored file entity to its public response. */
    FileResponse toResponse(StoredFile file);
}
