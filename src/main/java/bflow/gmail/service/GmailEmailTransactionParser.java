package bflow.gmail.service;

import bflow.receipts.enums.ReceiptTransactionType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a transaction from a transactional email without persisting its body. */
@Component
public class GmailEmailTransactionParser {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(?:total|monto|importe|amount|valor)\\s*[:=]?\\s*(?:USD|US\\$|\\$|€|₡)?\\s*([0-9][0-9.,]{0,16})");
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "(?i)(?:(?:USD|US dollars?|\\$)\\s*([0-9][0-9.,]{0,16})|"
                    + "([0-9][0-9.,]{0,16})\\s*(?:USD|US dollars?|d[oó]lares?|dollars?))");
    private static final Pattern MERCHANT = Pattern.compile(
            "(?i)(?:comercio|merchant|establecimiento|producto|product)\\s*[:=]\\s*(.{2,80}?)(?=\\s+(?:fecha|total|monto|importe|cantidad|código|codigo|detalle)\\s*[:=]|$)");
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/\\d{4})\\b");
    private static final List<String> INCOME_WORDS = List.of(
            "recibiste", "has recibido", "dinero recibido", "pago recibido", "pago acreditado",
            "depósito", "deposito", "depósito recibido", "deposito recibido", "abono",
            "abono recibido", "acreditado", "acreditación", "acreditacion", "saldo acreditado",
            "transferencia recibida", "transferencia entrante", "ingreso", "ingreso recibido",
            "reembolso", "devolución", "devolucion", "cashback", "bonificación", "bonificacion",
            "you received", "money received", "payment received", "payment credited",
            "deposit received", "credited", "credit received", "funds received", "incoming transfer",
            "transfer received", "income received", "refund", "refund processed", "cash back",
            "cashback received", "payout", "disbursement");
    private static final List<String> EXPENSE_WORDS = List.of(
            "compra", "tu compra", "compra aprobada", "compra realizada", "pagaste", "has pagado",
            "pago con", "pago realizado", "pago aprobado", "pago exitoso", "pago confirmado",
            "cobro", "cobro realizado", "cargo", "cargo realizado", "cargo aprobado", "cargado a tu",
            "debitado", "débito", "debito", "consumo", "transacción exitosa", "transaccion exitosa",
            "orden confirmada", "pedido confirmado", "factura pagada", "purchase", "purchase successful",
            "purchase approved", "purchase confirmation", "you paid", "payment successful",
            "payment approved", "payment confirmed", "payment completed", "charge", "charged",
            "card charged", "debit card", "transaction completed", "transaction approved",
            "order confirmed", "order confirmation", "invoice paid");

    public boolean isTransactionEmail(final String body) {
        String text = plainText(body);
        return extractDirection(text) != null && findAmount(text) != null;
    }

    public ParsedEmailTransaction parse(final byte[] bytes) {
        String text = plainText(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        ReceiptTransactionType type = extractDirection(text);
        BigDecimal amount = findAmount(text);
        if (type == null || amount == null) return null;
        if (amount.signum() <= 0) return null;
        return new ParsedEmailTransaction(type, title(text), amount, date(text));
    }

    private BigDecimal findAmount(final String text) {
        Matcher labelled = AMOUNT.matcher(text);
        if (labelled.find()) return amount(labelled.group(1));
        Matcher currency = CURRENCY_AMOUNT.matcher(text);
        if (!currency.find()) return null;
        return amount(currency.group(1) == null ? currency.group(2) : currency.group(1));
    }

    private ReceiptTransactionType extractDirection(final String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (INCOME_WORDS.stream().anyMatch(normalized::contains)) return ReceiptTransactionType.INCOME;
        if (EXPENSE_WORDS.stream().anyMatch(normalized::contains)) return ReceiptTransactionType.EXPENSE;
        return null;
    }

    private String title(final String text) {
        Matcher merchant = MERCHANT.matcher(text);
        String title = merchant.find() ? merchant.group(1) : "Movimiento desde Gmail";
        title = title.replaceAll("[^\\p{L}0-9 .,'\\-()!?&@#%/+:_]", " ")
                .replaceAll("\\s+", " ").trim().replaceAll("[.,;:]+$", "").trim();
        return title.isBlank() ? "Movimiento desde Gmail" : title.substring(0, Math.min(50, title.length()));
    }

    private LocalDate date(final String text) {
        Matcher matcher = DATE.matcher(text);
        if (!matcher.find()) return null;
        try { return LocalDate.parse(matcher.group(1), DateTimeFormatter.ofPattern("d/M/uuuu")); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private BigDecimal amount(final String value) {
        try {
            String normalized = value.replace(".", "").replace(',', '.');
            if (value.lastIndexOf('.') > value.lastIndexOf(',')) normalized = value.replace(",", "");
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) { return null; }
    }

    private String plainText(final String value) {
        return HtmlUtils.htmlUnescape(value.replaceAll("(?is)<[^>]+>", " "))
                .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    public record ParsedEmailTransaction(ReceiptTransactionType type, String title,
                                         BigDecimal amount, LocalDate date) { }
}
