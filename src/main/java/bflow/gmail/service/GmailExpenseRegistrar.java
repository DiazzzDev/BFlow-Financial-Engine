package bflow.gmail.service;

import bflow.category.RepositoryCategory;
import bflow.category.enums.CategoryType;
import bflow.common.i18n.MessageService;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.services.ServiceExpense;
import bflow.income.DTO.IncomeRequest;
import bflow.income.ServiceIncome;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.repository.GmailImportRepository;
import bflow.notifications.service.NotificationService;
import bflow.receipts.enums.ReceiptTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GmailExpenseRegistrar {
    private final ServiceExpense serviceExpense;
    private final ServiceIncome serviceIncome;
    private final RepositoryCategory categoryRepository;
    private final GmailImportRepository importRepository;
    private final NotificationService notificationService;
    private final MessageService messageService;
    private final Clock businessClock;

    @Transactional
    public void register(final UUID importId, final String title,
                         final BigDecimal amount, final LocalDate date,
                         final ReceiptTransactionType type) {
        GmailImport item = importRepository.findById(importId)
                .orElseThrow(() -> new IllegalStateException("Gmail import not found"));
        UUID transactionId = type == ReceiptTransactionType.INCOME
                ? registerIncome(item, title, amount, date) : registerExpense(item, title, amount, date);
        item.setTransactionId(transactionId);
        item.setExpenseId(type == ReceiptTransactionType.EXPENSE ? transactionId : null);
        item.setTransactionType(type);
        item.setStatus("COMPLETED");
        item.setErrorCode(null);
        importRepository.save(item);
        if (type == ReceiptTransactionType.INCOME) {
            notificationService.sendIncomeRegistered(item.getUser().getId(), transactionId);
        } else {
            notificationService.sendExpenseRegistered(item.getUser().getId(), transactionId);
        }
    }

    private UUID registerExpense(final GmailImport item, final String title,
                                 final BigDecimal amount, final LocalDate date) {
        ExpenseRequest request = base(new ExpenseRequest(), item, title, amount, date, CategoryType.EXPENSE);
        return UUID.fromString(serviceExpense.newExpense(request, item.getUser().getId()).getId());
    }

    private UUID registerIncome(final GmailImport item, final String title,
                                final BigDecimal amount, final LocalDate date) {
        IncomeRequest request = base(new IncomeRequest(), item, title, amount, date, CategoryType.INCOME);
        return UUID.fromString(serviceIncome.newIncome(request, item.getUser().getId()).getId());
    }

    private <T extends bflow.common.financial.BaseTransactionRequest> T base(
            final T request, final GmailImport item, final String title,
            final BigDecimal amount, final LocalDate date, final CategoryType categoryType) {
        request.setWalletId(item.getWallet().getId());
        request.setCategoryId(categoryRepository
                .findFirstByNameIgnoreCaseAndType("OTHER", categoryType)
                .orElseThrow(() -> new IllegalStateException("Default transaction category is unavailable"))
                .getId());
        request.setTitle(title);
        request.setDescription(messageService.getForLanguage(
                "gmail.expense.description", item.getUser().getLanguage()));
        request.setAmount(amount.abs());
        request.setDate(date == null ? LocalDate.now(businessClock) : date);
        request.setSource("gmail");
        request.setRecurring(false);
        return request;
    }
}
