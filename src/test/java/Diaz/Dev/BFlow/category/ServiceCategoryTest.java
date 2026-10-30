package Diaz.Dev.BFlow.category;

import bflow.category.DTO.CategoryRequest;
import bflow.category.DTO.CategoryResponse;
import bflow.category.RepositoryCategory;
import bflow.category.ServiceCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCategoryTest {

    @Mock
    private RepositoryCategory repositoryCategory;

    @InjectMocks
    private ServiceCategory serviceCategory;

    @Test
    void creatingCategoryPersistsUserProvidedDetailsAsCustomCategory() {
        UUID categoryId = UUID.randomUUID();
        CategoryRequest request = new CategoryRequest();
        request.setName("Pet care");
        request.setType(CategoryType.EXPENSE);
        request.setIcon("paw");
        request.setColor("#336699");
        when(repositoryCategory.save(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category category = invocation.getArgument(0);
                    category.setId(categoryId);
                    return category;
                });

        CategoryResponse response = serviceCategory.create(request);

        assertEquals(categoryId, response.getId());
        assertEquals("Pet care", response.getName());
        assertEquals(CategoryType.EXPENSE, response.getType());
        assertEquals("paw", response.getIcon());
        assertEquals("#336699", response.getColor());
        verify(repositoryCategory).save(any(Category.class));
    }

    @Test
    void listingCategoriesReturnsTheAvailableCategoryDetails() {
        Category food = category("Food", CategoryType.EXPENSE, "utensils");
        Category salary = category("Salary", CategoryType.INCOME, "briefcase");
        when(repositoryCategory.findAll()).thenReturn(List.of(food, salary));

        List<CategoryResponse> response = serviceCategory.findAll();

        assertEquals(2, response.size());
        assertEquals("Food", response.get(0).getName());
        assertEquals(CategoryType.EXPENSE, response.get(0).getType());
        assertEquals("utensils", response.get(0).getIcon());
        assertEquals("Salary", response.get(1).getName());
        assertEquals(CategoryType.INCOME, response.get(1).getType());
        assertEquals("briefcase", response.get(1).getIcon());
        verify(repositoryCategory).findAll();
    }

    private Category category(
            final String name,
            final CategoryType type,
            final String icon
    ) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setType(type);
        category.setIcon(icon);
        return category;
    }
}
