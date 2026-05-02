package bflow.category;

import bflow.category.DTO.CategoryRequest;
import bflow.category.entity.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<Category> create(
            @RequestBody final CategoryRequest request
    ) {
        return ResponseEntity.ok(serviceCategory.create(request));
    }

    /**
     * Retrieves all categories.
     *
     * @return a list of all categories
     */
    @GetMapping
    public List<Category> getAll() {
        return serviceCategory.findAll();
    }

}
