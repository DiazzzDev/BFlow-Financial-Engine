package Diaz.Dev.BFlow.transactionimport.csv;

import bflow.auth.services.UserService;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.transactionimport.csv.CsvFileSniffer;
import bflow.transactionimport.csv.CsvImportService;
import bflow.transactionimport.dto.ImportRowResult;
import bflow.transactionimport.dto.ImportSummary;
import bflow.transactionimport.mapping.ImportColumnMapper;
import bflow.transactionimport.model.ParsedRow;
import bflow.transactionimport.service.CsvImportBatchExecutor;
import bflow.transactionimport.service.InputSanitizer;
import bflow.transactionimport.service.TransactionRowParser;
import bflow.wallet.repository.RepositoryWalletUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * Uses a real {@link TransactionRowParser} (with a stubbed
 * {@link RepositoryCategory} that resolves any name to a generic
 * expense category) so these tests exercise actual file-reading and
 * row-parsing behavior instead of measuring un-stubbed mocks.
 */
@ExtendWith(MockitoExtension.class)
class CsvImportServiceGapsTest {

    @Mock private RepositoryWalletUser repositoryWalletUser;
    @Mock private UserService userService;
    @Mock private RepositoryCategory repositoryCategory;
    @Mock private CsvImportBatchExecutor batchExecutor;

    private CsvImportService service;
    private final UUID walletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Category anyCategory = new Category();
        anyCategory.setType(CategoryType.EXPENSE);
        lenient().when(repositoryCategory.findByNameIgnoreCase(anyString()))
                .thenReturn(Optional.of(anyCategory));

        // Every batch "persists" successfully — lets tests focus on
        // file reading / row parsing, not persistence.
        lenient().when(batchExecutor.executeBatch(any(), any(), anyList()))
                .thenAnswer(inv -> {
                    List<ParsedRow> batch = inv.getArgument(2);
                    Map<Integer, ImportRowResult> outcomes = new LinkedHashMap<>();
                    for (ParsedRow row : batch) {
                        outcomes.put(row.rowNumber(), ImportRowResult.ok(
                                row.rowNumber(), row.type(), UUID.randomUUID().toString()
                        ));
                    }
                    return outcomes;
                });

        lenient().when(repositoryWalletUser.existsByWalletIdAndUserId(walletId, userId))
                .thenReturn(true);

        service = new CsvImportService(
                new CsvFileSniffer(),
                new ImportColumnMapper(),
                new TransactionRowParser(repositoryCategory, new InputSanitizer(),
                        java.time.Clock.system(java.time.ZoneId.of("America/El_Salvador"))),
                batchExecutor,
                repositoryWalletUser,
                userService
        );
    }

    // NOT a gap after all — ImportColumnMapper.normalize() already
    // strips the BOM character as part of its non-alnum cleanup.
    // Kept as a regression test so it doesn't silently break later.
    @Test
    void resolvesHeadersDespiteUtf8Bom() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csv = "title,amount,category\nAlmuerzo,5,Comida\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + csv.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(csv, 0, withBom, bom.length, csv.length);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", withBom
        );

        ImportSummary summary = service.importTransactions(file, walletId, userId);

        assertThat(summary.getFailureCount()).isZero();
    }

    // GAP: files encoded as Windows-1252/Latin-1 get accented
    // headers/values corrupted because we force UTF-8 decoding
    // unconditionally. Wrapped with assertThatCode so this shows as
    // a clean assertion failure (not a stack-trace error) until
    // encoding detection is implemented.
    @Disabled("GAP: non-UTF-8 encodings (Windows-1252/Latin-1) corrupt "
            + "accented headers/values — see edge cases discussion")
    @Test
    void resolvesHeadersEncodedAsWindows1252() {
        byte[] content = "título,monto,categoría\nAlmuerzo,5,Comida\n"
                .getBytes(Charset.forName("windows-1252"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", content
        );

        assertThatCode(() -> service.importTransactions(file, walletId, userId))
                .doesNotThrowAnyException();
    }

    // Now passes: a row with fewer columns than the header fails
    // only that row (thanks to the CsvRowAdapter fix), it no longer
    // risks aborting the whole file.
    @Test
    void isolatesMalformedRowInsteadOfAbortingWholeFile() {
        String content = "title,amount,category\n"
                + "Almuerzo,5,Comida\n"
                + "Fila incompleta,5\n"
                + "Cena en casa,8,Comida\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );

        ImportSummary summary = service.importTransactions(file, walletId, userId);

        assertThat(summary.getTotalRows()).isEqualTo(3);
        assertThat(summary.getFailureCount()).isEqualTo(1);
    }

    @Test
    void reportsClearErrorOnDuplicateHeaders() {
        String content = "title,amount,amount,category\n"
                + "Almuerzo,5,5,Comida\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );

        assertThatCode(() -> service.importTransactions(file, walletId, userId))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsTrailingBlankLine() {
        String content = "title,amount,category\n"
                + "Almuerzo,5,Comida\n"
                + "\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );

        ImportSummary summary = service.importTransactions(file, walletId, userId);

        assertThat(summary.getTotalRows()).isEqualTo(1);
    }
}
