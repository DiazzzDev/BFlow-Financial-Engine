package Diaz.Dev.BFlow.category;

import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.category.CategoryValidator;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryValidatorTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private CategoryValidator categoryValidator;

    @Test
    void expenseTransactionAcceptsAnExpenseCategory() {
        assertDoesNotThrow(() -> categoryValidator.validateExpenseCategory(
                category(CategoryType.EXPENSE)));

        verifyNoInteractions(messageService);
    }

    @Test
    void incomeTransactionAcceptsAnIncomeCategory() {
        assertDoesNotThrow(() -> categoryValidator.validateIncomeCategory(
                category(CategoryType.INCOME)));

        verifyNoInteractions(messageService);
    }

    @Test
    void expenseTransactionRejectsMissingCategoryWithLocalizedMessage() {
        when(messageService.get("category.notFound"))
                .thenReturn("Category not found.");

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> categoryValidator.validateExpenseCategory(null));

        assertEquals("Category not found.", exception.getMessage());
        verify(messageService).get("category.notFound");
    }

    @Test
    void incomeTransactionRejectsMissingCategoryWithLocalizedMessage() {
        when(messageService.get("category.notFound"))
                .thenReturn("Category not found.");

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> categoryValidator.validateIncomeCategory(null));

        assertEquals("Category not found.", exception.getMessage());
    }

    @Test
    void expenseTransactionRejectsIncomeCategory() {
        when(messageService.get(
                "category.invalidType.expense", CategoryType.INCOME))
                .thenReturn("Choose an expense category.");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> categoryValidator.validateExpenseCategory(
                        category(CategoryType.INCOME)));

        assertEquals("Choose an expense category.", exception.getMessage());
    }

    @Test
    void incomeTransactionRejectsExpenseCategory() {
        when(messageService.get(
                "category.invalidType.income", CategoryType.EXPENSE))
                .thenReturn("Choose an income category.");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> categoryValidator.validateIncomeCategory(
                        category(CategoryType.EXPENSE)));

        assertEquals("Choose an income category.", exception.getMessage());
    }

    private Category category(final CategoryType type) {
        Category category = new Category();
        category.setType(type);
        return category;
    }
}
