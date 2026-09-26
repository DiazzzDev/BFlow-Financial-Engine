package bflow.category.mapper;

import bflow.category.DTO.CategoryResponse;
import bflow.category.DTO.CategoryRequest;
import bflow.category.entity.Category;
import bflow.common.mapper.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapping;

import java.util.List;

/** Maps category entities to their API response representation. */
@Mapper(config = BaseMapperConfig.class)
public interface CategoryMapper {

    /**
     * Maps a category to a response. MapStruct supplies the null check and
     * maps fields with matching names at compile time.
     *
     * @param category category entity, possibly {@code null}
     * @return mapped response, or {@code null} for a null entity
     */
    CategoryResponse toResponse(Category category);

    /** Maps a category list without a hand-written iteration. */
    List<CategoryResponse> toResponses(List<Category> categories);

    /** Maps request fields while leaving server-managed fields untouched. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "systemDefined", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Category toEntity(CategoryRequest request);

    /** Reuses the response mapping in the inverse direction. */
    @InheritInverseConfiguration(name = "toResponse")
    @Mapping(target = "systemDefined", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Category fromResponse(CategoryResponse response);
}
