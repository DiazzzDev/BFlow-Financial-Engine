package Diaz.Dev.BFlow.transactionimport.service;

import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.i18n.MessageService;
import bflow.common.financial.TransactionDatePolicy;
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
class TransactionRowParserEdgeCasesTest {

    @Mock private RepositoryCategory repositoryCategory;
    @Mock private InputSanitizer inputSanitizer;
    @Mock private MessageService messageService;

    private TransactionRowParser parser;
    private ColumnMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new ImportColumnMapper().resolve(
                List.of("title", "amount", "category", "date", "type"));
        when(inputSanitizer.sanitize(anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        parser = new TransactionRowParser(repositoryCategory, inputSanitizer,
                new TransactionDatePolicy(
                        Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"),
                                ZoneId.of("America/El_Salvador")),
                        messageService));
    }

    @Test
    void parsesCommaDecimalAndKeepsPositiveMagnitude() {
        stubCategory("Comida", CategoryType.EXPENSE);

        ParsedRow result = parse(Map.of("title", "Almuerzo", "amount", "12,50",
                "category", "Comida", "date", "2026-10-01", "type", "EXPENSE"));

        assertThat(result.amount()).isEqualByComparingTo("12.50");
        assertThat(result.type()).isEqualTo("EXPENSE");
    }

    @Test
    void rejectsTransferRowsEvenWhenCategoryIsNotTransfer() {
        stubCategory("Comida", CategoryType.EXPENSE);

        assertThatThrownBy(() -> parse(Map.of("title", "Transferencia",
                "amount", "10", "category", "Comida", "type", "TRANSFER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transfers are not supported");
    }

    @Test
    void rejectsDeclaredTypeThatConflictsWithCategory() {
        stubCategory("Salario", CategoryType.INCOME);

        assertThatThrownBy(() -> parse(Map.of("title", "Pago mensual",
                "amount", "100", "category", "Salario", "type", "EXPENSE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match category");
    }

    @Test
    void rejectsImpossibleStrictDateInsteadOfNormalizingIt() {
        assertThatThrownBy(() -> parse(Map.of("title", "Compra semanal",
                "amount", "10", "category", "Comida", "date", "31/02/2026")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date");
    }

    private ParsedRow parse(Map<String, String> values) {
        ImportRow row = values::get;
        return parser.parse(row, 2, mapping);
    }

    private void stubCategory(String name, CategoryType type) {
        Category category = new Category();
        category.setType(type);
        when(repositoryCategory.findByNameIgnoreCase(name))
                .thenReturn(Optional.of(category));
    }
}
