package bflow.category;

import bflow.category.DTO.CategoryRequest;
import bflow.category.DTO.CategoryResponse;
import bflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * REST controller for managing financial transaction categories.
 * Provides endpoints for creating and retrieving categories.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class ControllerCategory {

    /**
     * Service for category business logic operations.
     */
    private final ServiceCategory serviceCategory;

    /**
     * Creates a new category from the provided request.
     *
     * @param request the category request containing category details
     * @return a ResponseEntity containing the created category
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse>  create(
            @Valid @RequestBody final CategoryRequest request
    ) {
        return ApiResponse.success("Categoría creada",
                serviceCategory.create(request), "/api/v1/categories");
    }

    /**
     * Retrieves all categories.
     *
     * @return a list of all categories
     */
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAll() {
        return ApiResponse.success("Categorías obtenidas",
                serviceCategory.findAll(), "/api/v1/categories");
    }

}
