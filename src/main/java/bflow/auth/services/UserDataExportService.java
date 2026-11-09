package bflow.auth.services;

import bflow.auth.repository.RepositoryUser;
import bflow.expenses.RepositoryExpense;
import bflow.expenses.entity.Expense;
import bflow.income.RepositoryIncome;
import bflow.income.entity.Income;
import bflow.wallet.repository.RepositoryWalletUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Builds a privacy-safe export of the authenticated user's personal data. */
@Service
@RequiredArgsConstructor
public class UserDataExportService {
    private final RepositoryUser users;
    private final RepositoryWalletUser walletUsers;
    private final RepositoryExpense expenses;
    private final RepositoryIncome incomes;

    @Transactional(readOnly = true)
    public Map<String, Object> export(final UUID userId) {
        var user = users.findById(userId).orElseThrow();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName());
        profile.put("language", user.getLanguage());
        profile.put("emailVerified", user.isEmailVerified());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("status", user.getStatus());

        var memberships = walletUsers.findByUserId(userId).stream().map(membership -> {
            var wallet = membership.getWallet();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("walletId", wallet.getId());
            item.put("name", wallet.getName());
            item.put("description", wallet.getDescription());
            item.put("currency", wallet.getCurrency());
            item.put("balance", wallet.getBalance());
            item.put("initialValue", wallet.getInitialValue());
            item.put("role", membership.getRole());
            item.put("defaultWallet", membership.isDefaultWallet());
            item.put("joinedAt", membership.getCreatedAt());
            return item;
        }).toList();

        return Map.of(
                "format", "bflow-user-data-export-v1",
                "exportedAt", java.time.Instant.now(),
                "profile", profile,
                "wallets", memberships,
                "expenses", expenses.findByContributorIdOrderByDateAscCreatedAtAsc(userId)
                        .stream().map(this::expense).toList(),
                "incomes", incomes.findByContributorIdOrderByDateAscCreatedAtAsc(userId)
                        .stream().map(this::income).toList()
        );
    }

    private Map<String, Object> expense(final Expense value) {
        Map<String, Object> item = transaction(value.getId(), value.getTitle(),
                value.getDescription(), value.getAmount(), value.getDate(),
                value.getWallet(), value.getCategory(), value.getSource(), value.getCreatedAt());
        item.put("recurring", value.getRecurring());
        item.put("recurrencePattern", value.getRecurrencePattern());
        return item;
    }

    private Map<String, Object> income(final Income value) {
        Map<String, Object> item = transaction(value.getId(), value.getTitle(),
                value.getDescription(), value.getAmount(), value.getDate(),
                value.getWallet(), value.getCategory(), value.getSource(), value.getCreatedAt());
        item.put("recurring", value.getRecurring());
        item.put("recurrencePattern", value.getRecurrencePattern());
        return item;
    }

    private Map<String, Object> transaction(final UUID id, final String title,
            final String description, final java.math.BigDecimal amount,
            final java.time.LocalDate date, final bflow.wallet.entities.Wallet wallet,
            final bflow.category.entity.Category category, final String source,
            final java.time.Instant createdAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("description", description);
        item.put("amount", amount);
        item.put("date", date);
        item.put("walletId", wallet.getId());
        item.put("categoryId", category == null ? null : category.getId());
        item.put("categoryName", category == null ? null : category.getName());
        item.put("source", source);
        item.put("createdAt", createdAt);
        return item;
    }
}
