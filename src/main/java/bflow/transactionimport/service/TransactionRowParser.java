package bflow.transactionimport.service;

import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.transactionimport.enums.ImportTransactionType;
import bflow.transactionimport.mapping.ColumnMapping;
import bflow.transactionimport.mapping.ImportField;
import bflow.transactionimport.model.ImportRow;
import bflow.transactionimport.model.ParsedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.format.DateTimeParseException;

/**
 * Parses and validates a single {@link ImportRow} into a
 * {@link ParsedRow}, regardless of the row's original source format
 * (CSV, XLSX, ...). Shared by every format-specific import service so
 * business rules (required fields, category/type inference, date
 * defaulting) live in exactly one place.
 */
@Component
@RequiredArgsConstructor
public class TransactionRowParser {

    /** Repository for category entity lookups. */
    private final RepositoryCategory repositoryCategory;

    private final InputSanitizer inputSanitizer;

    /** Business-calendar clock used when an import row omits its date. */
    private final Clock clock;

    /**
     * Parses and validates one row.
     *
     * @param row the source row, already header-addressable
     * @param rowNumber 1-based row number, for error reporting
     * @param mapping the resolved column mapping for this file
     * @return the validated, ready-to-persist row
     * @throws IllegalArgumentException on any validation failure
     */
    public ParsedRow parse(
            final ImportRow row,
            final int rowNumber,
            final ColumnMapping mapping
    ) {
        String title = inputSanitizer.sanitize(
                "title", required(get(row, mapping, ImportField.TITLE), "title")
        );
        String description = inputSanitizer.sanitize(
                "description", get(row, mapping, ImportField.DESCRIPTION)
        );

        BigDecimal amount = parseAmount(get(row, mapping, ImportField.AMOUNT));
        LocalDate date = parseDate(get(row, mapping, ImportField.DATE));
        String categoryName = required(
                get(row, mapping, ImportField.CATEGORY), "category"
        );

        Category category = repositoryCategory.findByNameIgnoreCase(categoryName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category '" + categoryName + "' not found"
                ));

        if (category.getType() == CategoryType.TRANSFER) {
            throw new IllegalArgumentException(
                    "Category '" + categoryName
                            + "' is a transfer category; not supported"
            );
        }

        ImportTransactionType inferredType =
                category.getType() == CategoryType.EXPENSE
                        ? ImportTransactionType.EXPENSE
                        : ImportTransactionType.INCOME;

        String rawType = get(row, mapping, ImportField.TYPE);
        if (rawType != null && !rawType.isBlank()) {
            ImportTransactionType declaredType = parseType(rawType);
            if (declaredType != inferredType) {
                throw new IllegalArgumentException(
                        "type '" + rawType + "' does not match category '"
                                + categoryName + "' (" + inferredType + ")"
                );
            }
        }

        if (title.length() < 5 || title.length() > 50) {
            throw new IllegalArgumentException(
                    "title must be between 5 and 50 characters"
            );
        }

        return new ParsedRow(
                rowNumber, inferredType.name(), title.trim(), description,
                amount.abs(), date, category.getId()
        );
    }

    private ImportTransactionType parseType(final String rawType) {
        String normalized = rawType.trim().toUpperCase();
        if ("TRANSFER".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Transfers are not supported via import"
            );
        }
        try {
            return ImportTransactionType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid type '" + rawType + "'. Expected EXPENSE or INCOME"
            );
        }
    }

    private BigDecimal parseAmount(final String rawAmount) {
        try {
            BigDecimal amount = new BigDecimal(
                    required(rawAmount, "amount").trim()
            );
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("amount must not be zero");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid amount '" + rawAmount + "'"
            );
        }
    }

    /**
     * Parses the date column. Unlike title/amount/category, a missing
     * or blank date is not an error — it defaults to today, since not
     * every user-supplied file will include one.
     */
    private LocalDate parseDate(final String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date '" + rawDate + "'. Expected yyyy-MM-dd"
            );
        }
    }

    private String required(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing '" + field + "' column");
        }
        return value;
    }

    private String get(
            final ImportRow row, final ColumnMapping mapping, final ImportField field
    ) {
        String header = mapping.header(field);
        return header == null ? null : row.get(header);
    }
}
