package bflow.category;

import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.i18n.MessageService;
import bflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validator for category operations.
 * Provides reusable validation logic for financial transactions.
 */
@Component
@RequiredArgsConstructor
public class CategoryValidator {

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Validates that a category exists and is of type EXPENSE.
     * Used for expense transactions.
     *
     * @param category the category to validate
     * @throws ResourceNotFoundException if category is null
     * @throws IllegalArgumentException if category type is not EXPENSE
     */
    public void validateExpenseCategory(final Category category) {
        if (category == null) {
            throw new ResourceNotFoundException(
                    messageService.get("category.notFound")
            );
        }

        if (category.getType() != CategoryType.EXPENSE) {
            throw new IllegalArgumentException(
                    messageService.get(
                            "category.invalidType.expense", category.getType()
                    )
            );
        }
    }

    /**
     * Validates that a category exists and is of type INCOME.
     * Used for income transactions.
     *
     * @param category the category to validate
     * @throws ResourceNotFoundException if category is null
     * @throws IllegalArgumentException if category type is not INCOME
     */
    public void validateIncomeCategory(final Category category) {
        if (category == null) {
            throw new ResourceNotFoundException(
                    messageService.get("category.notFound")
            );
        }

        if (category.getType() != CategoryType.INCOME) {
            throw new IllegalArgumentException(
                    messageService.get(
                            "category.invalidType.income", category.getType()
                    )
            );
        }
    }
}
