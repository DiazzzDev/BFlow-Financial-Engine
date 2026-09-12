package bflow.category.DTO;

import bflow.category.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRequest {
    /**
     * The name of the category.
     */
    @NotBlank(message = "{category.name.required}")
    private String name;

    /**
     * The type of category (INCOME, EXPENSE, or TRANSFER).
     */
    private CategoryType type;

    /**
     * The icon identifier for the category.
     */
    private String icon;

    /**
     * The color code for the category.
     */
    private String color;
}
