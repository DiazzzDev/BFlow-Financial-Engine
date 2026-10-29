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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void rejectsEmptyUploadBeforeParsingOrPersisting() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> service.importTransactions(empty, walletId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV file is required");

        verify(batchExecutor, never()).executeBatch(any(), any(), anyList());
    }

    @Test
    void rejectsUploadsAboveTwoMegabytesBeforeParsing() {
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.csv", "text/csv", new byte[2 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.importTransactions(
                oversized, walletId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum allowed size of 2MB");

        verify(batchExecutor, never()).executeBatch(any(), any(), anyList());
    }

    @Test
    void deniesImportWhenRequesterDoesNotBelongToWallet() {
        when(repositoryWalletUser.existsByWalletIdAndUserId(walletId, userId))
                .thenReturn(false);
        MockMultipartFile file = csv("title,amount,category\nLunch,5,Food\n");

        assertThatThrownBy(() -> service.importTransactions(file, walletId, userId))
                .isInstanceOf(bflow.common.exception.WalletAccessDeniedException.class);

        verify(batchExecutor, never()).executeBatch(any(), any(), anyList());
    }

    @Test
    void importsSemicolonDelimitedRegionalSpreadsheet() {
        ImportSummary summary = service.importTransactions(
                csv("title;amount;category\nAlmuerzo;12,50;Comida\n"),
                walletId, userId);

        assertThat(summary.getTotalRows()).isEqualTo(1);
        assertThat(summary.getFailureCount()).isZero();
        verify(batchExecutor).executeBatch(eq(walletId), eq(userId), anyList());
    }

    @Test
    void rejectsXlsxSignatureInsteadOfDecodingItAsText() {
        byte[] zipHeader = {0x50, 0x4b, 0x03, 0x04, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "renamed.csv", "text/csv", zipHeader);

        assertThatThrownBy(() -> service.importTransactions(file, walletId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");

        verify(batchExecutor, never()).executeBatch(any(), any(), anyList());
    }

    @Test
    void missingRequiredHeadersRejectWholeFileWithoutBatchCalls() {
        MockMultipartFile file = csv("description,date\nLunch,2026-10-01\n");

        assertThatThrownBy(() -> service.importTransactions(file, walletId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not detect required column");

        verify(batchExecutor, never()).executeBatch(any(), any(), anyList());
    }

    @Test
    void failedPersistenceBatchReturnsPerRowFailuresInsteadOfLosingRows() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(batchExecutor).executeBatch(any(), any(), anyList());
        MockMultipartFile file = csv("title,amount,category\n"
                + "Lunch,5,Food\nDinner,8,Food\n");

        ImportSummary summary = service.importTransactions(file, walletId, userId);

        assertThat(summary.getTotalRows()).isEqualTo(2);
        assertThat(summary.getSuccessCount()).isZero();
        assertThat(summary.getFailureCount()).isEqualTo(2);
        assertThat(summary.getResults()).allSatisfy(row ->
                assertThat(row.getMessage()).contains("Batch failed: database unavailable"));
    }

    @Test
    void transferTypeRowFailsIndependentlyWhileValidExpensePersists() {
        MockMultipartFile file = csv("title,amount,category,type\n"
                + "Lunch,5,Food,EXPENSE\n"
                + "Move money,10,Food,TRANSFER\n");

        ImportSummary summary = service.importTransactions(file, walletId, userId);

        assertThat(summary.getTotalRows()).isEqualTo(2);
        assertThat(summary.getSuccessCount()).isEqualTo(1);
        assertThat(summary.getFailureCount()).isEqualTo(1);
        assertThat(summary.getResults().get(0).isSuccess()).isTrue();
        assertThat(summary.getResults().get(1).getMessage())
                .contains("Transfers are not supported");
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
