package bflow.merchant;

import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for detecting expense categories based on merchant patterns.
 */
@Service
@RequiredArgsConstructor
public final class MerchantDetectionService {

    /**
     * Repository for merchant pattern operations.
     */
    private final MerchantPatternRepository merchantPatternRepository;

    /**
     * Repository for category operations.
     */
    private final RepositoryCategory categoryRepository;

    /**
     * Detect the category for an expense based on its description.
     *
     * @param normalizedDescription the normalized expense description
     * @return the detected category or default category
     */
    public Category detectCategory(final String normalizedDescription) {

        if (normalizedDescription == null || normalizedDescription.isBlank()) {
            return getDefaultCategory();
        }

        List<MerchantPattern> patterns =
                merchantPatternRepository.findAllByOrderByPriorityDesc();

        for (MerchantPattern pattern : patterns) {
            if (normalizedDescription.contains(pattern.getPattern())) {
                return pattern.getCategory();
            }
        }

        return getDefaultCategory();
    }

    private Category getDefaultCategory() {
        return categoryRepository.findByNameIgnoreCase("Others")
                .orElse(null);
    }
}
