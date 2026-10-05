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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionRowParserTest {

    @Mock
    private RepositoryCategory repositoryCategory;

    @Mock
    private InputSanitizer inputSanitizer;

    private TransactionRowParser parser;

    private ColumnMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new ImportColumnMapper().resolve(
            List.of("title", "amount", "category", "date")
        );
        when(inputSanitizer.sanitize(anyString(), any()))
            .thenAnswer(inv -> inv.getArgument(1));
        parser = new TransactionRowParser(repositoryCategory, inputSanitizer,
                Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"),
                        ZoneId.of("America/El_Salvador")));
    }

    private ImportRow rowOf(final Map<String, String> values) {
        return values::get;
    }

    @Test
    void infersExpenseTypeFromCategory() {
        Category comida = new Category();
        comida.setType(CategoryType.EXPENSE);
        when(repositoryCategory.findByNameIgnoreCase("Comida"))
                .thenReturn(Optional.of(comida));

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Almuerzo", "amount", "5.50",
                "category", "Comida", "date", "2026-09-01"
        )), 1, mapping);

        assertThat(result.type()).isEqualTo("EXPENSE");
        assertThat(result.amount()).isEqualByComparingTo("5.50");
    }

    @Test
    void defaultsToTodayWhenDateMissing() {
        Category salario = new Category();
        salario.setType(CategoryType.INCOME);
        when(repositoryCategory.findByNameIgnoreCase("Salario"))
                .thenReturn(Optional.of(salario));

        ParsedRow result = parser.parse(rowOf(Map.of(
                "title", "Pago quincena", "amount", "500",
                "category", "Salario"
        )), 1, mapping);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    void rejectsTransferCategory() {
        Category transferencia = new Category();
        transferencia.setType(CategoryType.TRANSFER);
        when(repositoryCategory.findByNameIgnoreCase(anyString()))
                .thenReturn(Optional.of(transferencia));

        assertThatThrownBy(() -> parser.parse(rowOf(Map.of(
                "title", "Movimiento", "amount", "100",
                "category", "Entre cuentas"
        )), 1, mapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not supported");
    }
}
