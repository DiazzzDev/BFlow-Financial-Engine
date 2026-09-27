package bflow.category;

import bflow.category.DTO.CategoryRequest;
import bflow.category.DTO.CategoryResponse;
import bflow.category.entity.Category;
import bflow.common.financial.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceCategory {

    /**
     * Repository for category database operations.
     */
    private final RepositoryCategory repositoryCategory;

    /**
     * Creates a new category from the provided request.
     * Initializes system-defined flag to false for user-created categories.
     *
     * @param request the category request containing category details
     * @return the created category response
     */
    public CategoryResponse create(final CategoryRequest request) {

        Category category = TransactionMapper
                .mapCategoryRequestToEntity(request);
        category.setSystemDefined(false);
        category.setCreatedAt(Instant.now());

        Category saved = repositoryCategory.save(category);
        return from(saved);
    }

    /**
     * Retrieves all categories from the database.
     *
     * @return a list of all category responses
     */
    public List<CategoryResponse> findAll() {
        return TransactionMapper.mapCategoriesToResponses(
                repositoryCategory.findAll()
        );
    }

    /**
     * Builds a response DTO from a Category entity.
     *
     * @param category the source entity
     * @return the mapped response DTO
     */
    private CategoryResponse from(final Category category) {
        return TransactionMapper.mapCategoryToResponse(category);
    }

}
