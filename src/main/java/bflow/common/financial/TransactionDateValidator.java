package bflow.common.financial;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Applies the shared transaction-date window during request validation. */
@Component
@RequiredArgsConstructor
public class TransactionDateValidator
        implements ConstraintValidator<ValidTransactionDate, LocalDate> {

    private final TransactionDatePolicy policy;
    private boolean allowNull;

    @Override
    public void initialize(final ValidTransactionDate constraint) {
        allowNull = constraint.allowNull();
    }

    @Override
    public boolean isValid(
            final LocalDate value,
            final ConstraintValidatorContext context
    ) {
        return value == null ? allowNull : policy.isValid(value);
    }
}
