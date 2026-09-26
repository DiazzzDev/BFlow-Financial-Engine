package bflow.notifications.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.notifications.DTO.NotificationResponse;
import bflow.notifications.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps notification entities to API responses. */
@Mapper(config = BaseMapperConfig.class)
public interface NotificationMapper {

    /** Maps a notification entity to its response DTO. */
    @Mapping(target = "type", source = "type")
    NotificationResponse toResponse(Notification notification);
}
