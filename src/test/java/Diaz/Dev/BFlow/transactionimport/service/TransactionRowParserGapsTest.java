package Diaz.Dev.BFlow.transactionimport.service;

import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.transactionimport.mapping.ColumnMapping;
import bflow.transactionimport.mapping.ImportColumnMapper;
import bflow.transactionimport.model.ImportRow;
import bflow.transactionimport.model.ParsedRow;
import bflow.transactionimport.service.InputSanitizer;
import bflow.transactionimport.service.TransactionRowParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private TransactionRowParser parser;

    private ColumnMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new ImportColumnMapper().resolve(
                List.of("title", "amount", "category", "date")
        );
        when(inputSanitizer.sanitize(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
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
}