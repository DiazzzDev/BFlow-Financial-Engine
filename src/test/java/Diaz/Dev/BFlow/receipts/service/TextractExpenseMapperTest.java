package Diaz.Dev.BFlow.receipts.service;

import bflow.receipts.enums.ReceiptTransactionType;
import bflow.receipts.service.TextractExpenseMapper;
import bflow.receipts.service.ReceiptStatusTransitionService.ReceiptOcrExtraction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.model.ExpenseDetection;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.ExpenseType;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisResponse;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextractExpenseMapperTest {

    private final TextractExpenseMapper mapper =
            new TextractExpenseMapper(new ObjectMapper());

    @Test
    void emptyResponseProducesManualExpenseDraft() {
        ReceiptOcrExtraction result = mapper.map(
                GetExpenseAnalysisResponse.builder().expenseDocuments(List.of()).build());

        assertThat(result.suggestedType()).isEqualTo(ReceiptTransactionType.EXPENSE);
        assertThat(result.suggestedTitle()).isNull();
        assertThat(result.suggestedAmount()).isNull();
        assertThat(result.suggestedDate()).isNull();
        assertThat(result.confidenceScore()).isNull();
        assertThat(result.rawOcrPayloadJson()).contains("summaryFields");
    }

    @Test
    void mapsVendorCurrencyThousandsSeparatorAndIsoDate() {
        ReceiptOcrExtraction result = mapper.map(response(
                field("VENDOR_NAME", "  Supermarket ", 90f),
                field("TOTAL", "$1,234.50", 98f),
                field("INVOICE_RECEIPT_DATE", "2026-10-01", 80f)));

        assertThat(result.suggestedTitle()).isEqualTo("Supermarket");
        assertThat(result.suggestedAmount()).isEqualByComparingTo("1234.50");
        assertThat(result.suggestedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.confidenceScore()).isEqualByComparingTo("89.33");
    }

    @Test
    void negativeOcrTotalBecomesPositiveExpenseMagnitude() {
        ReceiptOcrExtraction result = mapper.map(response(field("TOTAL", "-$12.75", 99f)));

        assertThat(result.suggestedAmount()).isEqualByComparingTo("12.75");
    }

    @Test
    void invalidTotalAndUnknownDateRemainNullForManualReview() {
        ReceiptOcrExtraction result = mapper.map(response(
                field("TOTAL", "not a number", 90f),
                field("INVOICE_RECEIPT_DATE", "31st of Smarch", 90f)));

        assertThat(result.suggestedAmount()).isNull();
        assertThat(result.suggestedDate()).isNull();
    }

    @Test
    void duplicateFieldsUseHighestConfidenceCandidate() {
        ReceiptOcrExtraction result = mapper.map(response(
                field("VENDOR_NAME", "Wrong Shop", 30f),
                field("VENDOR_NAME", "Right Shop", 95f),
                field("TOTAL", "10.00", 80f)));

        assertThat(result.suggestedTitle()).isEqualTo("Right Shop");
    }

    @Test
    void supportsLatAmAndLongFormDates() {
        ReceiptOcrExtraction latAm = mapper.map(response(
                field("INVOICE_RECEIPT_DATE", "31/10/2026", 90f)));
        ReceiptOcrExtraction longForm = mapper.map(response(
                field("INVOICE_RECEIPT_DATE", "October 31, 2026", 90f)));

        assertThat(latAm.suggestedDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(longForm.suggestedDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void serializesSummaryFieldsAsPlainJson() {
        ReceiptOcrExtraction result = mapper.map(response(
                field("VENDOR_NAME", "Market", 95f)));

        assertThat(result.rawOcrPayloadJson()).contains("VENDOR_NAME", "Market", "95.0");
    }

    private GetExpenseAnalysisResponse response(ExpenseField... fields) {
        return GetExpenseAnalysisResponse.builder()
                .expenseDocuments(ExpenseDocument.builder()
                        .summaryFields(fields).build())
                .build();
    }

    private ExpenseField field(String type, String value, float confidence) {
        return ExpenseField.builder()
                .type(ExpenseType.builder().text(type).build())
                .valueDetection(ExpenseDetection.builder()
                        .text(value).confidence(confidence).build())
                .build();
    }
}
