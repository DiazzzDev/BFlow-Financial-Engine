package bflow.auth.mapper;

import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.entities.User;
import bflow.common.mapper.BaseMapperConfig;
import org.mapstruct.Mapper;

/** Maps a user entity to its public profile response. */
@Mapper(config = BaseMapperConfig.class)
public interface UserProfileMapper {

    /** Maps matching profile fields without exposing persistence-only data. */
    UserProfileResponse toResponse(User user);
}
