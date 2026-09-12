package Diaz.Dev.BFlow.budget.services;

import bflow.budget.enums.BudgetScope;
import bflow.budget.services.BudgetValidationService;
import bflow.common.exception.InvalidBudgetDateException;
import bflow.common.exception.InvalidBudgetScopeException;
import bflow.common.exception.InvalidBudgetThresholdException;
import bflow.common.i18n.MessageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for BudgetValidationService. Pure validation logic with
 * no dependencies — previously had zero coverage despite guarding
 * every budget creation/update against invalid dates, threshold
 * ordering, scope/field mismatches, and non-positive amounts.
 */
class BudgetValidationServiceTest {

    private BudgetValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new BudgetValidationService(
                org.mockito.Mockito.mock(MessageService.class)
        );
    }

    // ---- validateStartDate ----

    @Test
    void validateStartDate_null_throwsInvalidBudgetDateException() {
        assertThrows(InvalidBudgetDateException.class,
                () -> validationService.validateStartDate(null));
    }

    @Test
    void validateStartDate_tomorrow_throwsInvalidBudgetDateException() {
        assertThrows(InvalidBudgetDateException.class,
                () -> validationService.validateStartDate(
                        LocalDate.now().plusDays(1)));
    }

    @Test
    void validateStartDate_today_isAllowed() {
        assertDoesNotThrow(() -> validationService.validateStartDate(
                LocalDate.now()));
    }

    @Test
    void validateStartDate_past_isAllowed() {
        assertDoesNotThrow(() -> validationService.validateStartDate(
                LocalDate.now().minusYears(1)));
    }

    // ---- validateThresholds ----

    @Test
    void validateThresholds_warningLessThanCritical_isAllowed() {
        assertDoesNotThrow(
                () -> validationService.validateThresholds(80, 95));
    }

    @Test
    void validateThresholds_warningEqualsCritical_throwsException() {
        // Boundary: equal thresholds are ambiguous (both statuses
        // would fire at the exact same percentage) and rejected.
        assertThrows(InvalidBudgetThresholdException.class,
                () -> validationService.validateThresholds(80, 80));
    }

    @Test
    void validateThresholds_warningGreaterThanCritical_throwsException() {
        assertThrows(InvalidBudgetThresholdException.class,
                () -> validationService.validateThresholds(95, 80));
    }

    @Test
    void validateThresholds_bothNull_isAllowed() {
        // Thresholds are optional on patch — null means "don't change".
        assertDoesNotThrow(
                () -> validationService.validateThresholds(null, null));
    }

    @Test
    void validateThresholds_onlyWarningProvided_isAllowed() {
        assertDoesNotThrow(
                () -> validationService.validateThresholds(80, null));
    }

    @Test
    void validateThresholds_onlyCriticalProvided_isAllowed() {
        assertDoesNotThrow(
                () -> validationService.validateThresholds(null, 95));
    }

    // ---- validateAmount ----

    @Test
    void validateAmount_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateAmount(null));
    }

    @Test
    void validateAmount_zero_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateAmount(BigDecimal.ZERO));
    }

    @Test
    void validateAmount_exactlyOne_isAllowed() {
        // Boundary: the docs say ">= 1", confirm 1 itself passes.
        assertDoesNotThrow(
                () -> validationService.validateAmount(BigDecimal.ONE));
    }

    @Test
    void validateAmount_justBelowOne_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateAmount(
                        new BigDecimal("0.99")));
    }

    @Test
    void validateAmount_negative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateAmount(
                        BigDecimal.valueOf(-100)));
    }

    // ---- validateBudgetScope: WALLET ----

    @Test
    void validateBudgetScope_wallet_withWalletIdOnly_isAllowed() {
        assertDoesNotThrow(() -> validationService.validateBudgetScope(
                BudgetScope.WALLET, UUID.randomUUID(), null));
    }

    @Test
    void validateBudgetScope_wallet_missingWalletId_throwsException() {
        assertThrows(InvalidBudgetScopeException.class,
                () -> validationService.validateBudgetScope(
                        BudgetScope.WALLET, null, null));
    }

    @Test
    void validateBudgetScope_wallet_withCategoryId_throwsException() {
        // WALLET scope must NOT carry a categoryId — this is what
        // distinguishes it from WALLET_CATEGORY. A client sending
        // both by mistake must be rejected, not silently narrowed.
        assertThrows(InvalidBudgetScopeException.class,
                () -> validationService.validateBudgetScope(
                        BudgetScope.WALLET, UUID.randomUUID(),
                        UUID.randomUUID()));
    }

    // ---- validateBudgetScope: WALLET_CATEGORY ----

    @Test
    void validateBudgetScope_walletCategory_withBothIds_isAllowed() {
        assertDoesNotThrow(() -> validationService.validateBudgetScope(
                BudgetScope.WALLET_CATEGORY, UUID.randomUUID(),
                UUID.randomUUID()));
    }

    @Test
    void validateBudgetScope_walletCategory_missingCategoryId_throwsException() {
        assertThrows(InvalidBudgetScopeException.class,
                () -> validationService.validateBudgetScope(
                        BudgetScope.WALLET_CATEGORY, UUID.randomUUID(),
                        null));
    }

    @Test
    void validateBudgetScope_walletCategory_missingWalletId_throwsException() {
        assertThrows(InvalidBudgetScopeException.class,
                () -> validationService.validateBudgetScope(
                        BudgetScope.WALLET_CATEGORY, null,
                        UUID.randomUUID()));
    }

    // ---- validateBudgetScope: CATEGORY_GLOBAL ----

    @Test
    void validateBudgetScope_categoryGlobal_withCategoryIdOnly_isAllowed() {
        assertDoesNotThrow(() -> validationService.validateBudgetScope(
                BudgetScope.CATEGORY_GLOBAL, null, UUID.randomUUID()));
    }

    @Test
    void validateBudgetScope_categoryGlobal_missingCategoryId_throwsException() {
        assertThrows(InvalidBudgetScopeException.class,
                () -> validationService.validateBudgetScope(
                        BudgetScope.CATEGORY_GLOBAL, null, null));
    }

    @Test
    void validateBudgetScope_categoryGlobal_withWalletId_throwsException() {
        // The whole point of CATEGORY_GLOBAL is "across every wallet"
        // — a walletId here would silently narrow the scope and
        // contradict the budget's own semantics, so it's rejected.
        assertThrows(InvalidBudgetScopeException.class,
                () -> validationService.validateBudgetScope(
                        BudgetScope.CATEGORY_GLOBAL, UUID.randomUUID(),
                        UUID.randomUUID()));
    }

    // ---- validateBudgetConstraints (composition of the above) ----

    @Test
    void validateBudgetConstraints_invalidThresholds_failsBeforeScopeCheck() {
        // Even with a scope violation present too, the threshold
        // check runs first — confirms the two validations aren't
        // silently short-circuited in the wrong order.
        assertThrows(InvalidBudgetThresholdException.class,
                () -> validationService.validateBudgetConstraints(
                        BudgetScope.WALLET, null, null, 90, 80));
    }

    @Test
    void validateBudgetConstraints_validThresholdsInvalidScope_throwsScopeException() {
        assertThrows(InvalidBudgetScopeException.class,
                () -> validationService.validateBudgetConstraints(
                        BudgetScope.WALLET, null, null, 80, 90));
    }

    @Test
    void validateBudgetConstraints_allValid_doesNotThrow() {
        assertDoesNotThrow(() -> validationService.validateBudgetConstraints(
                BudgetScope.WALLET_CATEGORY, UUID.randomUUID(),
                UUID.randomUUID(), 80, 95));
    }
}