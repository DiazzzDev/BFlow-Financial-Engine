package bflow.transactionimport.csv;

import bflow.auth.services.UserService;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.transactionimport.dto.ImportRowResult;
import bflow.transactionimport.dto.ImportSummary;
import bflow.transactionimport.mapping.ColumnMapping;
import bflow.transactionimport.mapping.ImportColumnMapper;
import bflow.transactionimport.mapping.ImportField;
import bflow.transactionimport.model.ParsedRow;
import bflow.transactionimport.service.CsvImportBatchExecutor;
import bflow.transactionimport.service.TransactionRowParser;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CSV-specific entry point for transaction import. Sniffs the file
 * before parsing to reject non-CSV binaries and to auto-detect the
 * delimiter, then adapts each record to
 * {@link bflow.transactionimport.model.ImportRow}, delegating all
 * parsing/validation to {@link TransactionRowParser} and all
 * persistence to {@link CsvImportBatchExecutor}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final int MAX_ROWS = 2000;
    private static final int BATCH_SIZE = 100;
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    private final CsvFileSniffer csvFileSniffer;
    private final ImportColumnMapper columnMapper;
    private final TransactionRowParser rowParser;
    private final CsvImportBatchExecutor batchExecutor;
    private final RepositoryWalletUser repositoryWalletUser;
    private final UserService userService;

    /**
     * Imports expenses/incomes from a CSV file into a single wallet.
     *
     * @param file the uploaded CSV file
     * @param walletId the wallet to import transactions into
     * @param userId the authenticated user performing the import
     * @return a per-row summary of the import, in original file order
     */
    public ImportSummary importTransactions(
            final MultipartFile file,
            final UUID walletId,
            final UUID userId
    ) {
        log.info(
                "CSV import requested: walletId={}, userId={}, filename={}",
                walletId, userId, file != null ? file.getOriginalFilename() : null
        );

        userService.validateUserActive(userId);

        if (!repositoryWalletUser.existsByWalletIdAndUserId(walletId, userId)) {
            log.warn(
                    "CSV import denied: userId={} has no access to walletId={}",
                    userId, walletId
            );
            throw new WalletAccessDeniedException(
                    "You do not have access to this wallet"
            );
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "CSV file exceeds the maximum allowed size of 2MB"
            );
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Unable to read CSV file: " + e.getMessage(), e
            );
        }

        csvFileSniffer.assertLooksLikeCsv(content);
        char delimiter = csvFileSniffer.detectDelimiter(content);

        List<ImportRowResult> results = new ArrayList<>();
        List<ParsedRow> pending = new ArrayList<>();

        try (
                InputStreamReader reader = new InputStreamReader(
                        new ByteArrayInputStream(content), detectCharset(content)
                );
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setDelimiter(delimiter)
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreHeaderCase(true)
                        .setIgnoreSurroundingSpaces(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)
        ) {
            ColumnMapping mapping = columnMapper.resolve(
                    new ArrayList<>(parser.getHeaderNames())
            );

            int rowNumber = 0;

            for (CSVRecord record : parser) {
                rowNumber++;

                if (rowNumber > MAX_ROWS) {
                    results.add(ImportRowResult.failed(
                            rowNumber, null,
                            "Row skipped: file exceeds the maximum of "
                                    + MAX_ROWS + " rows"
                    ));
                    continue;
                }

                try {
                    pending.add(rowParser.parse(
                            new CsvRowAdapter(record), rowNumber, mapping
                    ));
                    results.add(null); // filled in after batching
                } catch (IllegalArgumentException e) {
                    String rawType = mapping.has(ImportField.TYPE)
                            ? record.get(mapping.header(ImportField.TYPE))
                            : null;
                    log.debug(
                            "Row {} rejected during validation: {}",
                            rowNumber, e.getMessage()
                    );
                    results.add(ImportRowResult.failed(
                            rowNumber, rawType, e.getMessage()
                    ));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Unable to read CSV file: " + e.getMessage(), e
            );
        }

        Map<Integer, ImportRowResult> batchOutcomes = new LinkedHashMap<>();
        for (int i = 0; i < pending.size(); i += BATCH_SIZE) {
            List<ParsedRow> batch = pending.subList(
                    i, Math.min(i + BATCH_SIZE, pending.size())
            );
            try {
                Map<Integer, ImportRowResult> outcomes =
                        batchExecutor.executeBatch(walletId, userId, batch);
                batchOutcomes.putAll(outcomes);
                log.info(
                        "Batch [{}-{}] persisted for walletId={}: {} rows",
                        i + 1, i + batch.size(), walletId, outcomes.size()
                );
            } catch (RuntimeException e) {
                log.error(
                        "Batch [{}-{}] failed for walletId={}: {}",
                        i + 1, i + batch.size(), walletId, e.getMessage(), e
                );
                for (ParsedRow row : batch) {
                    batchOutcomes.put(row.rowNumber(), ImportRowResult.failed(
                            row.rowNumber(), row.type(),
                            "Batch failed: " + e.getMessage()
                    ));
                }
            }
        }

        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                results.set(i, batchOutcomes.get(i + 1));
            }
        }

        ImportSummary summary = new ImportSummary();
        summary.setTotalRows(results.size());
        summary.setSuccessCount((int) results.stream()
                .filter(r -> r != null && r.isSuccess()).count());
        summary.setFailureCount(
                summary.getTotalRows() - summary.getSuccessCount()
        );
        summary.setResults(results);

        log.info(
                "CSV import finished: walletId={}, userId={}, total={}, "
                        + "success={}, failed={}",
                walletId, userId, summary.getTotalRows(),
                summary.getSuccessCount(), summary.getFailureCount()
        );

        return summary;
    }

    private Charset detectCharset(final byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            log.info("CSV is not valid UTF-8; decoding as Windows-1252");
            return Charset.forName("windows-1252");
        }
    }
}