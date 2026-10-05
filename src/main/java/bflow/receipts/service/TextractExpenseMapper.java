package bflow.receipts.service;

import bflow.receipts.enums.ReceiptTransactionType;
import bflow.receipts.service.ReceiptStatusTransitionService.ReceiptOcrExtraction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps a Textract {@code GetExpenseAnalysis} response into the
 * editable draft shown to the user before they confirm it.
 *
 * <p>Only the first {@code ExpenseDocument} in the response is used.
 * {@code AnalyzeExpense} returns one document per page group it
 * detected; for the single-receipt-photo use case this is almost
 * always exactly one. Multi-page invoices with several distinct
 * documents in one file are a known gap — flagged here rather than
 * silently dropped.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextractExpenseMapper {

    /** Textract's field type for the merchant/vendor name. */
    private static final String FIELD_VENDOR_NAME = "VENDOR_NAME";

    /** Textract's field type for the total amount. */
    private static final String FIELD_TOTAL = "TOTAL";

    /** Textract's field type for the invoice/receipt date. */
    private static final String FIELD_DATE = "INVOICE_RECEIPT_DATE";

    /**
     * Date formats Textract is observed to return for {@link
     * #FIELD_DATE}. Tried in order; if none match, the date is left
     * null and the user fills it in manually — better than a wrong
     * guess.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy")
    );

    /** Used to serialize the reconstructed summary fields as JSON. */
    private final ObjectMapper objectMapper;

    /**
     * Maps a Textract response to the receipt's suggested draft.
     *
     * @param response the {@code GetExpenseAnalysis} response
     * @return the mapped suggestion, ready to persist
     */
    public ReceiptOcrExtraction map(final GetExpenseAnalysisResponse response) {
        ExpenseDocument document = response.expenseDocuments().stream()
                .findFirst()
                .orElse(null);

        if (document == null) {
            log.warn("Textract returned no expense documents for job {}",
                    response.responseMetadata() != null
                            ? response.responseMetadata().requestId()
                            : "unknown");
            return new ReceiptOcrExtraction(
                    ReceiptTransactionType.EXPENSE,
                    null, null, null, null,
                    toJson(List.of())
            );
        }

        Map<String, ExpenseField> summary = indexByType(document);

        ExpenseField vendorField = summary.get(FIELD_VENDOR_NAME);
        ExpenseField totalField = summary.get(FIELD_TOTAL);
        ExpenseField dateField = summary.get(FIELD_DATE);

        return new ReceiptOcrExtraction(
                ReceiptTransactionType.EXPENSE,
                textOf(vendorField),
                amountOf(totalField),
                dateOf(dateField),
                averageConfidence(vendorField, totalField),
                toJson(document.summaryFields())
        );
    }

    private Map<String, ExpenseField> indexByType(
        final ExpenseDocument document
    ) {
        Map<String, ExpenseField> byType = new LinkedHashMap<>();
        for (ExpenseField field : document.summaryFields()) {
            if (
                field.type() == null
                || !StringUtils.hasText(field.type().text())
            ) {
                continue;
            }
            String type = field.type().text();
            ExpenseField current = byType.get(type);
            // Textract legitimately returns the same field type more
            // than once when it isn't sure which candidate is right
            // (e.g. two INVOICE_RECEIPT_DATE guesses on the same
            // receipt) — the worse guess is not reliably the second
            // one, so pick by confidence, not by order.
            if (
                current == null
                || confidenceOf(field) > confidenceOf(current)
            ) {
                byType.put(type, field);
            }
        }
        return byType;
    }

    private float confidenceOf(final ExpenseField field) {
        if (field.valueDetection() == null
                || field.valueDetection().confidence() == null) {
            return -1f;
        }
        return field.valueDetection().confidence();
    }

    private String textOf(final ExpenseField field) {
        if (field == null || field.valueDetection() == null) {
            return null;
        }
        String text = field.valueDetection().text();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private BigDecimal amountOf(final ExpenseField field) {
        String raw = textOf(field);
        if (raw == null) {
            return null;
        }
        // Textract's TOTAL value is usually plain digits, but can
        // carry a currency symbol or thousands separators depending
        // on the receipt's layout.
        String normalized = raw.replaceAll("[^0-9.,-]", "")
                .replace(",", "");
        try {
            // A receipt total represents a magnitude. Some OCR
            // outputs add a minus sign for a charge, but BFlow stores
            // expense and income amounts as positive values.
            return new BigDecimal(normalized).abs();
        } catch (NumberFormatException ex) {
            log.debug("Could not parse Textract TOTAL '{}' as a number", raw);
            return null;
        }
    }

    private LocalDate dateOf(final ExpenseField field) {
        String raw = textOf(field);
        if (raw == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        log.debug(
            "Could not parse Textract date '{}' with any known format", raw
        );
        return null;
    }

    private BigDecimal averageConfidence(final ExpenseField... fields) {
        List<Float> confidences = java.util.Arrays.stream(fields)
                .filter(Objects::nonNull)
                .map(field -> Optional.ofNullable(field.valueDetection())
                        .map(v -> v.confidence())
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        if (confidences.isEmpty()) {
            return null;
        }

        double average = confidences.stream()
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0);

        return BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Reconstructs every summary field Textract detected as a plain,
     * always-serializable structure. AWS SDK v2 model classes expose
     * fluent accessors (e.g. {@code text()}), not JavaBean-style
     * getters, so Jackson's default introspection can't dump them —
     * this builds an equivalent plain structure by hand instead.
     *
     * @param fields the summary fields to serialize
     * @return a JSON array of {@code {type, value, confidence}}
     */
    private String toJson(final List<ExpenseField> fields) {
        List<Map<String, Object>> plain = fields.stream()
                .map(field -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("type", field.type() != null
                            ? field.type().text() : null);
                    entry.put("value", field.valueDetection() != null
                            ? field.valueDetection().text() : null);
                    entry.put("confidence", field.valueDetection() != null
                            ? field.valueDetection().confidence() : null);
                    return entry;
                })
                .toList();

        try {
            return objectMapper.writeValueAsString(
                    Map.of("summaryFields", plain));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize Textract summary fields", ex);
            return "{}";
        }
    }
}
