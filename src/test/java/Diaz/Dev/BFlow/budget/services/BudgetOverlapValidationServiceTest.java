package Diaz.Dev.BFlow.budget.services;

import bflow.budget.DTO.BudgetRequest;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.PeriodType;
import bflow.budget.repository.RepositoryBudget;
import bflow.budget.services.BudgetOverlapValidationService;
import bflow.common.exception.BudgetOverlapException;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetOverlapValidationServiceTest {

    @Mock
    private RepositoryBudget repositoryBudget;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private BudgetOverlapValidationService validationService;

    @Test
    void createWalletBudgetIsAllowedWhenNoMatchingBudgetExists() {
        BudgetRequest request = request(BudgetScope.WALLET);
        UUID userId = UUID.randomUUID();
        when(repositoryBudget.existsByWalletIdAndUserIdAndScopeAndPeriod(
                request.getWalletId(), userId, BudgetScope.WALLET,
                PeriodType.MONTHLY)).thenReturn(false);

        validationService.validateCreateOverlap(request, userId);

        verify(repositoryBudget).existsByWalletIdAndUserIdAndScopeAndPeriod(
                request.getWalletId(), userId, BudgetScope.WALLET,
                PeriodType.MONTHLY);
    }

    @Test
    void createWalletCategoryBudgetRejectsAnExistingBudget() {
        BudgetRequest request = request(BudgetScope.WALLET_CATEGORY);
        UUID userId = UUID.randomUUID();
        when(repositoryBudget
                .existsByWalletIdAndUserIdAndCategoryIdAndPeriod(
                        request.getWalletId(), userId, request.getCategoryId(),
                        PeriodType.MONTHLY)).thenReturn(true);
        when(messageService.get("budget.overlap"))
                .thenReturn("A budget already exists for this period.");

        BudgetOverlapException exception = assertThrows(
                BudgetOverlapException.class,
                () -> validationService.validateCreateOverlap(request, userId));

        assertEquals("A budget already exists for this period.",
                exception.getMessage());
    }

    @Test
    void createGlobalCategoryBudgetChecksTheUsersCategoryAndPeriod() {
        BudgetRequest request = request(BudgetScope.CATEGORY_GLOBAL);
        UUID userId = UUID.randomUUID();
        when(repositoryBudget.existsByUserIdAndScopeAndCategoryIdAndPeriod(
                userId, BudgetScope.CATEGORY_GLOBAL, request.getCategoryId(),
                PeriodType.MONTHLY)).thenReturn(false);

        validationService.validateCreateOverlap(request, userId);

        verify(repositoryBudget)
                .existsByUserIdAndScopeAndCategoryIdAndPeriod(
                        userId, BudgetScope.CATEGORY_GLOBAL,
                        request.getCategoryId(), PeriodType.MONTHLY);
    }

    @Test
    void patchWalletBudgetExcludesTheBudgetBeingEdited() {
        Budget budget = budget();
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repositoryBudget
                .existsByWalletIdAndUserIdAndScopeAndPeriodAndIdNot(
                        walletId, userId, BudgetScope.WALLET,
                        PeriodType.MONTHLY, budget.getId()))
                .thenReturn(false);

        validationService.validatePatchOverlap(
                budget, BudgetScope.WALLET, walletId, null,
                PeriodType.MONTHLY, userId);

        verify(repositoryBudget)
                .existsByWalletIdAndUserIdAndScopeAndPeriodAndIdNot(
                        walletId, userId, BudgetScope.WALLET,
                        PeriodType.MONTHLY, budget.getId());
    }

    @Test
    void patchWalletCategoryBudgetRejectsAConflictingBudget() {
        Budget budget = budget();
        UUID walletId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repositoryBudget
                .existsByWalletIdAndUserIdAndCategoryIdAndPeriodAndIdNot(
                        walletId, userId, categoryId, PeriodType.MONTHLY,
                        budget.getId())).thenReturn(true);
        when(messageService.get("budget.overlap"))
                .thenReturn("A budget already exists for this period.");

        BudgetOverlapException exception = assertThrows(
                BudgetOverlapException.class,
                () -> validationService.validatePatchOverlap(
                        budget, BudgetScope.WALLET_CATEGORY, walletId,
                        categoryId, PeriodType.MONTHLY, userId));

        assertEquals("A budget already exists for this period.",
                exception.getMessage());
    }

    @Test
    void patchGlobalCategoryBudgetChecksScopeCategoryPeriodAndCurrentId() {
        Budget budget = budget();
        UUID categoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repositoryBudget
                .existsByUserIdAndScopeAndCategoryIdAndPeriodAndIdNot(
                        userId, BudgetScope.CATEGORY_GLOBAL, categoryId,
                        PeriodType.MONTHLY, budget.getId()))
                .thenReturn(false);

        validationService.validatePatchOverlap(
                budget, BudgetScope.CATEGORY_GLOBAL, null, categoryId,
                PeriodType.MONTHLY, userId);

        verify(repositoryBudget)
                .existsByUserIdAndScopeAndCategoryIdAndPeriodAndIdNot(
                        userId, BudgetScope.CATEGORY_GLOBAL, categoryId,
                        PeriodType.MONTHLY, budget.getId());
    }

    private BudgetRequest request(final BudgetScope scope) {
        BudgetRequest request = new BudgetRequest();
        request.setScope(scope);
        request.setPeriod(PeriodType.MONTHLY);
        request.setWalletId(UUID.randomUUID());
        request.setCategoryId(UUID.randomUUID());
        return request;
    }

    private Budget budget() {
        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());
        return budget;
    }
}
