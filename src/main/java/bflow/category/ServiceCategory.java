package bflow.category;

import bflow.category.DTO.CategoryRequest;
import bflow.category.DTO.CategoryResponse;
import bflow.category.entity.Category;
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

        Category category = new Category();
        category.setName(request.getName());
        category.setType(request.getType());
        category.setIcon(request.getIcon());
        category.setColor(request.getColor());
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
        return repositoryCategory.findAll()
                .stream()
                .map(this::from)
                .toList();
    }

    /**
     * Builds a response DTO from a Category entity.
     *
     * @param category the source entity
     * @return the mapped response DTO
     */
    private CategoryResponse from(final Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setType(category.getType());
        response.setIcon(category.getIcon());
        response.setColor(category.getColor());
        return response;
    }

}
