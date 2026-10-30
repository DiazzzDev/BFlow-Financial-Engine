package Diaz.Dev.BFlow.transactionimport.service;

import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.financial.InvalidTransactionDateException;
import bflow.common.financial.TransactionDatePolicy;
import bflow.common.i18n.MessageService;
import bflow.transactionimport.mapping.ColumnMapping;
import bflow.transactionimport.mapping.ImportColumnMapper;
import bflow.transactionimport.model.ImportRow;
import bflow.transactionimport.model.ParsedRow;
import bflow.transactionimport.service.InputSanitizer;
import bflow.transactionimport.service.TransactionRowParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers common locale-specific amount and date formats in imports.
 */
@ExtendWith(MockitoExtension.class)
class TransactionRowParserGapsTest {

    @Mock
    private RepositoryCategory repositoryCategory;

    @Mock
    private InputSanitizer inputSanitizer;

    @Mock
    private MessageService messageService;

    private TransactionRowParser parser;

    private ColumnMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new ImportColumnMapper().resolve(
                List.of("title", "amount", "category", "date",
                        "type", "description")
        );
        when(inputSanitizer.sanitize(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
        TransactionDatePolicy datePolicy = new TransactionDatePolicy(
                Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
                        ZoneId.of("America/El_Salvador")),
                messageService);
        parser = new TransactionRowParser(
                repositoryCategory, inputSanitizer, datePolicy);
    }

    private ImportRow rowOf(final Map<String, String> values) {
        return values::get;
    }

    @Test
    void parsesAmountWithThousandsSeparator() {
        Category comida = new Category();
        comida.setType(CategoryType.EXPENSE);
        when(repositoryCategory.findByNameIgnoreCase("Comida"))
                .thenReturn(Optional.of(comida));

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Compra grande", "amount", "1,500.00",
                "category", "Comida", "date", "2026-09-01"
        )), 1, mapping);

        assertThat(result.amount()).isEqualByComparingTo("1500.00");
    }

    @ParameterizedTest
    @CsvSource({
        "'1 234,56', 1234.56",
        "'1.234,56', 1234.56",
        "'1,234.56', 1234.56",
        "'1,234', 1234",
        "'1.234', 1234",
        "'1234,56', 1234.56",
        "'1234.56', 1234.56",
        "'1,2,3', 12.3",
        "'12.34.567', 1234567",
        "'1,234,567.89', 1234567.89",
        "'1.234.567,89', 1234567.89",
        "'-5.50', 5.50",
        "'0,25', 0.25",
        "'1 000', 1000",
        "'1.234.567', 1234567",
        "'123456', 123456",
        "'1,234,567', 1234567"
    })
    void parsesSupportedAmountSeparators(
            final String rawAmount,
            final String expectedAmount
    ) {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", rawAmount,
                "category", "Comida", "date", "2026-09-01"
        )), 1, mapping);

        assertThat(result.amount()).isEqualByComparingTo(expectedAmount);
    }

    @ParameterizedTest
    @CsvSource({
        "'0', 'amount must not be zero'",
        "'not-a-number', 'Invalid amount'",
        "'  ', 'Missing ''amount'' column'"
    })
    void rejectsMalformedAmounts(
            final String rawAmount,
            final String expectedMessage
    ) {
        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", rawAmount,
                "category", "Comida", "date", "2026-09-01"
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    @ParameterizedTest
    @CsvSource({
        "'abcd', false",
        "'abcde', true",
        "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', true",
        "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', false"
    })
    void enforcesTitleLengthBounds(
            final String title,
            final boolean accepted
    ) {
        stubExpenseCategory();

        if (accepted) {
            assertThat(parser.parse(rowOf(Map.of(
                    "title", title, "amount", "1",
                    "category", "Comida"
            )), 1, mapping).title()).isEqualTo(title);
        } else {
            assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                    "title", title, "amount", "1",
                    "category", "Comida"
            )), 1, mapping))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("title must be between");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "EXPENSE, EXPENSE",
        "' expense ', EXPENSE"
    })
    void acceptsMatchingExplicitType(
            final String rawType,
            final String expectedType
    ) {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida", "type", rawType
        )), 1, mapping);

        assertThat(result.type()).isEqualTo(expectedType);
    }

    @ParameterizedTest
    @CsvSource({
        "EXPENSE, INCOME",
        "INCOME, EXPENSE"
    })
    void rejectsExplicitTypeThatDoesNotMatchCategory(
            final String rawType,
            final String categoryType
    ) {
        Category category = new Category();
        category.setType(CategoryType.valueOf(categoryType));
        when(repositoryCategory.findByNameIgnoreCase("Comida"))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida", "type", rawType
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match category");
    }

    @ParameterizedTest
    @CsvSource({
        "cash, 'Invalid type'",
        "TRANSFER, 'Transfers are not supported'"
    })
    void rejectsUnsupportedExplicitTypes(
            final String rawType,
            final String expectedMessage
    ) {
        stubExpenseCategory();

        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida", "type", rawType
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void acceptsIncomeTypeForIncomeCategory() {
        Category salary = new Category();
        salary.setType(CategoryType.INCOME);
        when(repositoryCategory.findByNameIgnoreCase("Salario"))
                .thenReturn(Optional.of(salary));

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Pago mensual", "amount", "500.00",
                "category", "Salario", "type", "income"
        )), 1, mapping);

        assertThat(result.type()).isEqualTo("INCOME");
    }

    @Test
    void trimsTitleAfterValidationAndSanitizesDescription() {
        stubExpenseCategory();
        when(inputSanitizer.sanitize(
                org.mockito.ArgumentMatchers.eq("description"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn("Safe description");

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "  Almuerzo  ", "amount", "5.00",
                "category", "Comida", "description", "raw",
                "date", "2026-09-01"
        )), 7, mapping);

        assertThat(result.rowNumber()).isEqualTo(7);
        assertThat(result.title()).isEqualTo("Almuerzo");
        assertThat(result.description()).isEqualTo("Safe description");
    }

    @Test
    void parsesCommonLatAmDateFormat() {
        Category comida = new Category();
        comida.setType(CategoryType.EXPENSE);
        when(repositoryCategory.findByNameIgnoreCase("Comida"))
                .thenReturn(Optional.of(comida));

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida", "date", "01/09/2026"
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @ParameterizedTest
    @CsvSource({
        "05-10-2026, 2026-10-05",
        "5/10/2026, 2026-10-05",
        "5-10-2026, 2026-10-05"
    })
    void parsesStrictDayFirstDateFormats(
            final String rawDate,
            final String expectedDate
    ) {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida", "date", rawDate
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.parse(expectedDate));
    }

    @ParameterizedTest
    @CsvSource({
        "not-a-date",
        "31/02/2026",
        "2026/10/05",
        "05.10.2026"
    })
    void rejectsInvalidDateFormats(final String rawDate) {
        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida", "date", rawDate
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date");
    }

    @Test
    void rejectsMissingRequiredTitleAndCategory() {
        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "amount", "5.00", "category", "Comida"
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing 'title' column");

        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00"
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing 'category' column");
    }

    @Test
    void rejectsUnknownCategory() {
        when(repositoryCategory.findByNameIgnoreCase("Inexistente"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Inexistente"
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category 'Inexistente' not found");
    }

    @Test
    void defaultsBlankDateToBusinessToday() {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida", "date", " "
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void defaultsMissingDateToBusinessToday() {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.00",
                "category", "Comida"
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void acceptsExactFiftyYearLimitInIsoFormat() {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Historical expense", "amount", "5.00",
                "category", "Comida", "date", "1976-10-05"
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.of(1976, 10, 5));
    }

    @Test
    void acceptsDayAfterFiftyYearLimitInIsoFormat() {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Historical expense", "amount", "5.00",
                "category", "Comida", "date", "1976-10-06"
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.of(1976, 10, 6));
    }

    @Test
    void rejectsDayBeforeFiftyYearLimitInIsoFormat() {
        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Historical expense", "amount", "5.00",
                "category", "Comida", "date", "1976-10-04"
        )), 1, mapping))
                .isInstanceOf(InvalidTransactionDateException.class);
    }

    @Test
    void rejectsFutureDateInIsoFormat() {
        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Future expense", "amount", "5.00",
                "category", "Comida", "date", "2026-10-06"
        )), 1, mapping))
                .isInstanceOf(InvalidTransactionDateException.class);
    }

    @Test
    void acceptsExactFiftyYearLimitInLatAmFormat() {
        stubExpenseCategory();

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Historical expense", "amount", "5.00",
                "category", "Comida", "date", "05/10/1976"
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.of(1976, 10, 5));
    }

    @Test
    void rejectsDayBeforeFiftyYearLimitInLatAmFormat() {
        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Historical expense", "amount", "5.00",
                "category", "Comida", "date", "04/10/1976"
        )), 1, mapping))
                .isInstanceOf(InvalidTransactionDateException.class);
    }

    @Test
    void rejectsFutureDateInLatAmFormat() {
        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Future expense", "amount", "5.00",
                "category", "Comida", "date", "06/10/2026"
        )), 1, mapping))
                .isInstanceOf(InvalidTransactionDateException.class);
    }

    private void stubExpenseCategory() {
        Category comida = new Category();
        comida.setType(CategoryType.EXPENSE);
        when(repositoryCategory.findByNameIgnoreCase("Comida"))
                .thenReturn(Optional.of(comida));
    }
}