package Diaz.Dev.BFlow.common.financial;

import bflow.common.financial.TransactionDatePolicy;
import bflow.common.financial.InvalidTransactionDateException;
import bflow.common.i18n.MessageService;
import bflow.common.financial.TransactionDateValidator;
import bflow.expenses.DTO.ExpenseRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionDatePolicyTest {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("America/El_Salvador");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate EARLIEST = LocalDate.of(1976, 10, 5);

    @Mock private MessageService messageService;
    private TransactionDatePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new TransactionDatePolicy(
                Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"),
                        BUSINESS_ZONE),
                messageService
        );
    }

    @Test
    void acceptsInclusiveHistoricalLimitAndValidRecentDates() {
        assertThat(policy.today()).isEqualTo(TODAY);
        assertThat(policy.isValid(EARLIEST)).isTrue();
        assertThat(policy.isValid(EARLIEST.plusDays(1))).isTrue();
        assertThat(policy.isValid(TODAY.minusDays(1))).isTrue();
        assertThat(policy.isValid(TODAY)).isTrue();
    }

    @Test
    void rejectsDatesBeforeLimitAndAfterBusinessToday() {
        assertThat(policy.isValid(EARLIEST.minusDays(1))).isFalse();
        assertThat(policy.isValid(LocalDate.of(1500, 1, 1))).isFalse();
        assertThat(policy.isValid(LocalDate.of(33, 1, 1))).isFalse();
        assertThat(policy.isValid(TODAY.plusDays(1))).isFalse();
        assertThat(policy.isValid(null)).isFalse();
    }

    @Test
    void requiredInvalidDateReportsItsPermittedBusinessDateRange() {
        when(messageService.get("transaction.date.outOfRangeWithBounds",
                EARLIEST, TODAY)).thenReturn("invalid date range");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> policy.validateRequired(LocalDate.of(1500, 1, 1)))
                .isInstanceOf(InvalidTransactionDateException.class)
                .hasMessage("invalid date range");
    }

    @Test
    void optionalDateMayBeOmittedOrUseAnAllowedPastDate() {
        assertThatCode(() -> policy.validateOptional(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateOptional(EARLIEST))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateOptional(TODAY.minusDays(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void optionalDateRejectsDatesOutsideTheSupportedWindow() {
        when(messageService.get("transaction.date.outOfRangeWithBounds",
                EARLIEST, TODAY)).thenReturn("invalid date range");

        assertThatThrownBy(() -> policy.validateOptional(
                EARLIEST.minusDays(1)))
                .isInstanceOf(InvalidTransactionDateException.class)
                .hasMessage("invalid date range");
        assertThatThrownBy(() -> policy.validateOptional(TODAY.plusDays(1)))
                .isInstanceOf(InvalidTransactionDateException.class)
                .hasMessage("invalid date range");
    }

    @Test
    void requiredDateRejectsMissingValueWithRequiredMessage() {
        when(messageService.get("transaction.date.required"))
                .thenReturn("Date is required");

        assertThatThrownBy(() -> policy.validateRequired(null))
                .isInstanceOf(InvalidTransactionDateException.class)
                .hasMessage("Date is required");
    }

    @Test
    void derivesBusinessDateFromElSalvadorZoneAtUtcDayBoundary() {
        TransactionDatePolicy boundaryPolicy = new TransactionDatePolicy(
                Clock.fixed(Instant.parse("2026-10-05T05:30:00Z"),
                        BUSINESS_ZONE),
                messageService);

        assertThat(boundaryPolicy.today())
                .isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(boundaryPolicy.isValid(LocalDate.of(2026, 10, 5)))
                .isFalse();
    }

    @Test
    void requestValidationEnforcesFiftyYearLimitForReceiptConfirmation() {
        Validator validator = requestValidator();
        bflow.receipts.DTO.ReceiptConfirmRequest request =
                new bflow.receipts.DTO.ReceiptConfirmRequest();

        request.setDate(EARLIEST.minusDays(1));
        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath()
                        .toString().equals("date"));

        request.setDate(EARLIEST);
        assertThat(validator.validate(request))
                .noneMatch(violation -> violation.getPropertyPath()
                        .toString().equals("date"));
    }

    @Test
    void requestValidationAssociatesHistoricalAndFutureDatesWithDateField() {
        Validator validator = requestValidator();

        assertDateViolation(validator, EARLIEST.minusDays(1));
        assertDateViolation(validator, TODAY.plusDays(1));
        assertNoDateViolation(validator, EARLIEST);
        assertNoDateViolation(validator, TODAY.minusDays(1));
    }

    private Validator requestValidator() {
        ValidatorFactory defaultFactory =
                Validation.buildDefaultValidatorFactory();
        ConstraintValidatorFactory factory = new ConstraintValidatorFactory() {
            @Override
            public <T extends ConstraintValidator<?, ?>> T getInstance(
                    final Class<T> validatorClass
            ) {
                if (validatorClass == TransactionDateValidator.class) {
                    return validatorClass.cast(
                            new TransactionDateValidator(policy));
                }
                return defaultFactory.getConstraintValidatorFactory()
                        .getInstance(validatorClass);
            }

            @Override
            public void releaseInstance(
                    final ConstraintValidator<?, ?> instance
            ) {
                defaultFactory.getConstraintValidatorFactory()
                        .releaseInstance(instance);
            }
        };
        ValidatorFactory configuredFactory = Validation.byDefaultProvider()
                .configure().constraintValidatorFactory(factory)
                .buildValidatorFactory();
        return configuredFactory.getValidator();
    }

    private void assertDateViolation(
            final Validator validator, final LocalDate date
    ) {
        ExpenseRequest request = new ExpenseRequest();
        request.setDate(date);
        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath()
                        .toString().equals("date"));
    }

    private void assertNoDateViolation(
            final Validator validator, final LocalDate date
    ) {
        ExpenseRequest request = new ExpenseRequest();
        request.setDate(date);
        assertThat(validator.validate(request))
                .noneMatch(violation -> violation.getPropertyPath()
                        .toString().equals("date"));
    }
}
