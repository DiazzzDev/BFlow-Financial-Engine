package Diaz.Dev.BFlow.gmail;

import bflow.gmail.service.GmailEmailTransactionParser;
import bflow.receipts.enums.ReceiptTransactionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailEmailTransactionParserTest {
    private final GmailEmailTransactionParser parser = new GmailEmailTransactionParser();

    @Test
    void parsesWompiLikeHtmlPurchaseWithoutAnAttachment() {
        String body = "<h1>Tu compra fue un éxito</h1><p>Comercio: Bflow Studio</p>"
                + "<p>Fecha: 24/09/2026</p><p>Total: $99.9</p>";

        var result = parser.parse(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(ReceiptTransactionType.EXPENSE);
        assertThat(result.amount()).isEqualByComparingTo("99.9");
        assertThat(result.title()).isEqualTo("Bflow Studio");
    }

    @Test
    void parsesIncomeOnlyWhenDirectionAndAmountArePresent() {
        var result = parser.parse("Recibiste un abono. Monto: $120.00"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(ReceiptTransactionType.INCOME);
        assertThat(parser.isTransactionEmail("Hola, tu factura está lista. Total: $120")).isFalse();
    }

    @Test
    void recognizesEnglishIncomeAndExpenseNotifications() {
        var income = parser.parse("Payment credited. Amount: USD 42.50"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var expense = parser.parse("Your card was charged. Merchant: Example Store. Total: $19.99"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(income).isNotNull();
        assertThat(income.type()).isEqualTo(ReceiptTransactionType.INCOME);
        assertThat(expense).isNotNull();
        assertThat(expense.type()).isEqualTo(ReceiptTransactionType.EXPENSE);
        assertThat(expense.title()).isEqualTo("Example Store");
    }
}
