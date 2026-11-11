package Diaz.Dev.BFlow.common.financial;

import bflow.expenses.DTO.ExpenseRequest;
import bflow.income.DTO.IncomeRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionRequestCategoryOptionalTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void expenseRequestDoesNotRequireCategoryId() {
        assertTrue(validator.validateProperty(
                new ExpenseRequest(), "categoryId").isEmpty());
    }

    @Test
    void incomeRequestDoesNotRequireCategoryId() {
        assertTrue(validator.validateProperty(
                new IncomeRequest(), "categoryId").isEmpty());
    }

}
