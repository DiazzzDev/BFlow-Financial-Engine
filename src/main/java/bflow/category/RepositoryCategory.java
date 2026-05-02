package bflow.category;

import bflow.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryCategory extends JpaRepository<Category, UUID> {
    /**
     * Find a category by name (case-insensitive).
     *
     * @param name the category name
     * @return an Optional containing the category if found
     */
    Optional<Category> findByNameIgnoreCase(String name);
}
