package bflow.category;

import bflow.category.DTO.CategoryRequest;
import bflow.category.DTO.CategoryResponse;
import bflow.common.i18n.MessageService;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for managing financial transaction categories.
 * Provides endpoints for creating and retrieving categories.
 */
@Tag(name = "Categories", description = "Management of income and expense categories")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class ControllerCategory {

    /**
     * Service for category business logic operations.
     */
    private final ServiceCategory serviceCategory;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Creates a new category from the provided request.
     *
     * @param request the category request containing category details
     * @return a ResponseEntity containing the created category
     */
    @Operation(
            summary = "Creates a new category from the provided request.",
            description = "Creates a new category from the provided request."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse>  create(
            @Valid @RequestBody final CategoryRequest request
    ) {
        return ApiResponse.success(
                messageService.get("category.created"),
                serviceCategory.create(request), "/api/v1/categories");
    }

    /**
     * Retrieves all categories.
     *
     * @return a list of all categories
     */
    @Operation(
            summary = "Retrieves all categories.",
            description = "Retrieves all categories."
    )
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAll() {
        return ApiResponse.success(
                messageService.get("category.retrieved"),
                serviceCategory.findAll(), "/api/v1/categories");
    }

}
