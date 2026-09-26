package Diaz.Dev.BFlow.common.financial;

import bflow.category.DTO.CategoryResponse;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.financial.TransactionMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tests the MapStruct-backed transaction mapping facade. */
class TransactionMapperTest {

    @Test
    void mapsCategoryFields() {
        UUID id = UUID.randomUUID();
        Category category = new Category();
        category.setId(id);
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);
        category.setIcon("utensils");
        category.setColor("#FF6B00");

        CategoryResponse response =
                TransactionMapper.mapCategoryToResponse(category);

        assertEquals(id, response.getId());
        assertEquals("Food", response.getName());
        assertEquals(CategoryType.EXPENSE, response.getType());
        assertEquals("utensils", response.getIcon());
        assertEquals("#FF6B00", response.getColor());
    }

    @Test
    void returnsNullForNullCategory() {
        assertNull(TransactionMapper.mapCategoryToResponse(null));
    }
}
