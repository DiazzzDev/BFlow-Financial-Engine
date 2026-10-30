package Diaz.Dev.BFlow.merchant;

import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.merchant.MerchantDetectionService;
import bflow.merchant.MerchantPattern;
import bflow.merchant.MerchantPatternRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantDetectionServiceTest {

    @Mock
    private MerchantPatternRepository merchantPatternRepository;

    @Mock
    private RepositoryCategory categoryRepository;

    @InjectMocks
    private MerchantDetectionService merchantDetectionService;

    @Test
    void descriptionMatchingMultiplePatternsUsesHighestPriorityMatch() {
        Category groceries = category("Groceries");
        Category transport = category("Transport");
        MerchantPattern highPriority = pattern("market", groceries);
        MerchantPattern lowerPriority = pattern("market central", transport);
        when(merchantPatternRepository.findAllByOrderByPriorityDesc())
                .thenReturn(List.of(highPriority, lowerPriority));

        Category result = merchantDetectionService
                .detectCategory("market central purchase");

        assertSame(groceries, result);
        verify(merchantPatternRepository).findAllByOrderByPriorityDesc();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void unmatchedDescriptionUsesTheDefaultOthersCategory() {
        Category others = category("Others");
        when(merchantPatternRepository.findAllByOrderByPriorityDesc())
                .thenReturn(List.of(pattern("market", category("Groceries"))));
        when(categoryRepository.findByNameIgnoreCase("Others"))
                .thenReturn(Optional.of(others));

        Category result = merchantDetectionService
                .detectCategory("unknown merchant");

        assertSame(others, result);
        verify(categoryRepository).findByNameIgnoreCase("Others");
    }

    @Test
    void blankDescriptionUsesDefaultWithoutQueryingMerchantPatterns() {
        Category others = category("Others");
        when(categoryRepository.findByNameIgnoreCase("Others"))
                .thenReturn(Optional.of(others));

        Category result = merchantDetectionService.detectCategory("  ");

        assertSame(others, result);
        verify(categoryRepository).findByNameIgnoreCase("Others");
        verifyNoInteractions(merchantPatternRepository);
    }

    @Test
    void nullDescriptionUsesDefaultWithoutQueryingMerchantPatterns() {
        Category others = category("Others");
        when(categoryRepository.findByNameIgnoreCase("Others"))
                .thenReturn(Optional.of(others));

        Category result = merchantDetectionService.detectCategory(null);

        assertSame(others, result);
        verify(categoryRepository).findByNameIgnoreCase("Others");
        verifyNoInteractions(merchantPatternRepository);
    }

    private MerchantPattern pattern(
            final String value,
            final Category category
    ) {
        MerchantPattern pattern = new MerchantPattern();
        pattern.setPattern(value);
        pattern.setCategory(category);
        return pattern;
    }

    private Category category(final String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }
}
