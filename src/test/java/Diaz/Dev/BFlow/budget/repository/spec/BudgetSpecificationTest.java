package Diaz.Dev.BFlow.budget.repository.spec;

import bflow.budget.DTO.BudgetSearchRequest;
import bflow.budget.entity.Budget;
import bflow.budget.enums.BudgetScope;
import bflow.budget.enums.BudgetStatus;
import bflow.budget.enums.PeriodType;
import bflow.budget.repository.spec.BudgetSpecification;
import bflow.category.entity.Category;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import bflow.wallet.entities.Wallet;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetSpecificationTest {

    @Test
    void optionalFiltersAreOmittedWhenValuesAreMissingOrBlank() {
        assertNull(BudgetSpecification.nameContains(null));
        assertNull(BudgetSpecification.nameContains("  "));
        assertNull(BudgetSpecification.hasWallet(null));
        assertNull(BudgetSpecification.hasCategory(null));
        assertNull(BudgetSpecification.hasScope(null));
        assertNull(BudgetSpecification.hasPeriod(null));
        assertNull(BudgetSpecification.hasStatus(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildAlwaysAppliesTheUserOwnershipPredicate() {
        UUID userId = UUID.randomUUID();
        Root<Budget> root = mock(Root.class);
        CriteriaQuery<Budget> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Object> userPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);
        when(root.<Object>get("user")).thenReturn(userPath);
        when(userPath.<Object>get("id")).thenReturn(idPath);
        when(criteriaBuilder.equal(idPath, userId)).thenReturn(expected);

        Predicate actual = BudgetSpecification.build(
                new BudgetSearchRequest(), userId)
                .toPredicate(root, query, criteriaBuilder);

        assertSame(expected, actual);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nameFilterMatchesWalletOrCategoryWithoutDuplicatingRows() {
        Root<Budget> root = mock(Root.class);
        CriteriaQuery<Budget> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Join<Budget, Wallet> walletJoin = mock(Join.class);
        Join<Budget, Category> categoryJoin = mock(Join.class);
        Path<String> walletName = mock(Path.class);
        Path<String> categoryName = mock(Path.class);
        Expression<String> walletLower = mock(Expression.class);
        Expression<String> categoryLower = mock(Expression.class);
        Predicate walletMatch = mock(Predicate.class);
        Predicate categoryMatch = mock(Predicate.class);
        Predicate expected = mock(Predicate.class);
        doReturn(walletJoin).when(root).join("wallet", JoinType.LEFT);
        doReturn(categoryJoin).when(root).join("category", JoinType.LEFT);
        when(walletJoin.<String>get("name")).thenReturn(walletName);
        when(categoryJoin.<String>get("name")).thenReturn(categoryName);
        when(criteriaBuilder.lower(walletName)).thenReturn(walletLower);
        when(criteriaBuilder.lower(categoryName)).thenReturn(categoryLower);
        when(criteriaBuilder.like(walletLower, "%market%"))
                .thenReturn(walletMatch);
        when(criteriaBuilder.like(categoryLower, "%market%"))
                .thenReturn(categoryMatch);
        when(criteriaBuilder.or(walletMatch, categoryMatch))
                .thenReturn(expected);

        Predicate actual = BudgetSpecification.nameContains(" Market ")
                .toPredicate(root, query, criteriaBuilder);

        assertSame(expected, actual);
        verify(query).distinct(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void optionalIdAndEnumFiltersCreatePredicatesWhenProvided() {
        Root<Budget> root = mock(Root.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<Object> relation = mock(Path.class);
        Path<Object> id = mock(Path.class);
        Predicate expected = mock(Predicate.class);
        when(root.<Object>get(anyString())).thenReturn(relation);
        when(relation.<Object>get("id")).thenReturn(id);
        when(criteriaBuilder.equal(any(Expression.class), any(Object.class)))
                .thenReturn(expected);
        UUID filterId = UUID.randomUUID();

        assertSame(expected, BudgetSpecification.hasWallet(filterId)
                .toPredicate(root, null, criteriaBuilder));
        assertSame(expected, BudgetSpecification.hasCategory(filterId)
                .toPredicate(root, null, criteriaBuilder));
        assertSame(expected, BudgetSpecification.hasScope(BudgetScope.WALLET)
                .toPredicate(root, null, criteriaBuilder));
        assertSame(expected, BudgetSpecification.hasPeriod(PeriodType.MONTHLY)
                .toPredicate(root, null, criteriaBuilder));
        assertSame(expected, BudgetSpecification.hasStatus(BudgetStatus.OK)
                .toPredicate(root, null, criteriaBuilder));
    }
}
