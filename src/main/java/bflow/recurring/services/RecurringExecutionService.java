package bflow.recurring.services;

import bflow.auth.services.UserServiceImpl;
import bflow.category.RepositoryCategory;
import bflow.category.entity.Category;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.WalletAccessDeniedException;
import bflow.expenses.DTO.ExpenseRequest;
import bflow.expenses.ServiceExpense;
import bflow.income.DTO.IncomeRequest;
import bflow.income.ServiceIncome;
import bflow.recurring.DTO.RecurringRequest;
import bflow.recurring.DTO.RecurringResponse;
import bflow.recurring.RepositoryRecurringTransaction;
import bflow.recurring.entity.RecurringTransaction;
import bflow.recurring.enums.RecurringType;
import bflow.wallet.RepositoryWalletUser;
import bflow.wallet.entities.Wallet;
import bflow.wallet.entities.WalletUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurringExecutionService {
    private final RepositoryRecurringTransaction repository;
    private final ServiceExpense serviceExpense;
    private final ServiceIncome serviceIncome;
    private final UserServiceImpl userService;
    private final RepositoryCategory repositoryCategory;
    private final RepositoryWalletUser repositoryWalletUser;

    public void executeDueTransactions() {

        List<RecurringTransaction> due =
                repository.findDueTransactions(LocalDate.now());

        for (RecurringTransaction recurring : due) {

            if (recurring.getType() == RecurringType.EXPENSE) {
                createExpense(recurring);
            } else {
                createIncome(recurring);
            }

            updateNextExecution(recurring);
        }
    }

    public List<RecurringResponse> getUserRecurring(UUID userId) {

        return repository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public RecurringResponse createRecurring(
            RecurringRequest request,
            UUID userId
    ) {

        userService.validateUserActive(userId);

        WalletUser walletUser = repositoryWalletUser
                .findByWalletIdAndUserId(request.getWalletId(), userId)
                .orElseThrow(() ->
                        new WalletAccessDeniedException(
                                "No access to wallet"
                        )
                );

        Wallet wallet = walletUser.getWallet();

        Category category = repositoryCategory
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category not found")
                );

        RecurringTransaction recurring = new RecurringTransaction();

        recurring.setTitle(request.getTitle());
        recurring.setDescription(request.getDescription());
        recurring.setAmount(request.getAmount());

        recurring.setWallet(wallet);
        recurring.setCategory(category);
        recurring.setUser(walletUser.getUser());

        recurring.setType(request.getType());
        recurring.setFrequency(request.getFrequency());
        recurring.setIntervalValue(request.getIntervalValue());

        recurring.setStartDate(request.getStartDate());
        recurring.setNextExecutionDate(request.getStartDate());

        recurring.setEndDate(request.getEndDate());
        recurring.setActive(true);

        RecurringTransaction saved = repository.save(recurring);

        return mapToResponse(saved);
    }

    private RecurringResponse mapToResponse(RecurringTransaction req) {
        RecurringResponse res = new RecurringResponse();
        res.setId(req.getId());
        res.setTitle(req.getTitle());
        res.setAmount(req.getAmount());
        res.setType(req.getType());
        res.setFrequency(req.getFrequency());
        res.setIntervalValue(req.getIntervalValue());
        res.setNextExecutionDate(req.getNextExecutionDate());
        res.setActive(req.getActive());
        res.setWalletId(req.getWallet().getId());
        res.setCategoryId(req.getCategory().getId());
        return res;
    }

    private void createExpense(RecurringTransaction recurring) {

        ExpenseRequest request = new ExpenseRequest();

        request.setTitle(recurring.getTitle());
        request.setDescription(recurring.getDescription());
        request.setAmount(recurring.getAmount());
        request.setDate(LocalDate.now());
        request.setWalletId(recurring.getWallet().getId());
        request.setCategoryId(recurring.getCategory().getId());
        request.setSource("recurring");
        request.setRecurring(true);

        serviceExpense.newExpense(
                request,
                recurring.getUser().getId()
        );
    }

    private void createIncome(RecurringTransaction recurring) {

        IncomeRequest request = new IncomeRequest();

        request.setTitle(recurring.getTitle());
        request.setDescription(recurring.getDescription());
        request.setAmount(recurring.getAmount());
        request.setDate(LocalDate.now());
        request.setWalletId(recurring.getWallet().getId());
        request.setCategoryId(recurring.getCategory().getId());
        request.setSource("recurring");
        request.setRecurring(true);

        serviceIncome.newIncome(
                request,
                recurring.getUser().getId()
        );
    }

    public void toggleRecurring(UUID id, UUID userId, boolean active) {

        RecurringTransaction recurring = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Recurring not found")
                );

        if (!recurring.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException("Access denied");
        }

        recurring.setActive(active);
    }

    private void updateNextExecution(RecurringTransaction recurring) {

        LocalDate next = recurring.getNextExecutionDate();

        switch (recurring.getFrequency()) {
            case DAILY -> next = next.plusDays(recurring.getIntervalValue());
            case WEEKLY -> next = next.plusWeeks(recurring.getIntervalValue());
            case MONTHLY -> next = next.plusMonths(recurring.getIntervalValue());
        }

        recurring.setNextExecutionDate(next);
    }

    public void deleteRecurring(UUID id, UUID userId) {

        RecurringTransaction recurring = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Recurring not found")
                );

        if (!recurring.getUser().getId().equals(userId)) {
            throw new WalletAccessDeniedException("Access denied");
        }

        repository.delete(recurring);
    }
}
