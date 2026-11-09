package Diaz.Dev.BFlow.gmail;

import bflow.auth.entities.User;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.category.enums.CategoryType;
import bflow.common.i18n.MessageService;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.DTO.ExpenseResponse;
import bflow.expenses.services.ServiceExpense;
import bflow.income.ServiceIncome;
import bflow.receipts.enums.ReceiptTransactionType;
import bflow.gmail.entity.GmailImport;
import bflow.gmail.repository.GmailImportRepository;
import bflow.gmail.service.GmailExpenseRegistrar;
import bflow.notifications.service.NotificationService;
import bflow.wallet.entities.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GmailExpenseRegistrarTest {
    @Mock private ServiceExpense serviceExpense;
    @Mock private ServiceIncome serviceIncome;
    @Mock private RepositoryCategory categoryRepository;
    @Mock private GmailImportRepository importRepository;
    @Mock private NotificationService notificationService;
    @Mock private MessageService messageService;

    @Test
    void createsExpenseWithoutAttachingReceiptThenNotifiesUser() {
        UUID importId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        GmailImport item = new GmailImport();
        item.setId(importId);
        item.setUser(user);
        item.setWallet(wallet);
        Category category = new Category();
        category.setId(categoryId);
        ExpenseResponse response = new ExpenseResponse();
        response.setId(expenseId.toString());
        when(importRepository.findById(importId)).thenReturn(Optional.of(item));
        when(categoryRepository.findFirstByNameIgnoreCaseAndType("OTHER", CategoryType.EXPENSE))
                .thenReturn(Optional.of(category));
        when(serviceExpense.newExpense(any(ExpenseRequest.class), org.mockito.ArgumentMatchers.eq(userId)))
                .thenReturn(response);
        when(messageService.getForLanguage("gmail.expense.description", user.getLanguage()))
                .thenReturn("Imported automatically from Gmail");

        GmailExpenseRegistrar registrar = new GmailExpenseRegistrar(serviceExpense,
                serviceIncome, categoryRepository, importRepository, notificationService, messageService,
                Clock.system(ZoneId.of("America/El_Salvador")));
        registrar.register(importId, "Coffee Shop", new BigDecimal("8.50"),
                LocalDate.of(2026, 10, 20), ReceiptTransactionType.EXPENSE);

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(serviceExpense).newExpense(captor.capture(), org.mockito.ArgumentMatchers.eq(userId));
        ExpenseRequest request = captor.getValue();
        assertThat(request.getSource()).isEqualTo("gmail");
        assertThat(request.getCategoryId()).isEqualTo(categoryId);
        assertThat(request.getReceiptFileId()).isNull();
        assertThat(item.getStatus()).isEqualTo("COMPLETED");
        assertThat(item.getExpenseId()).isEqualTo(expenseId);
        verify(notificationService).sendExpenseRegistered(userId, expenseId);
    }
}
