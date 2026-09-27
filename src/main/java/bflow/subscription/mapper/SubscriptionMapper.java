package bflow.subscription.mapper;

import bflow.common.mapper.BaseMapperConfig;
import bflow.subscription.dto.SubscriptionResponse;
import bflow.subscription.entities.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps subscription entities to public responses. */
@Mapper(config = BaseMapperConfig.class)
public interface SubscriptionMapper {

    /** Maps the associated plan name into the response. */
    @Mapping(target = "planName", source = "plan.name")
    SubscriptionResponse toResponse(Subscription subscription);
}
