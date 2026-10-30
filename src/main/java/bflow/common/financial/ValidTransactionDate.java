package bflow.common.financial;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Bean Validation constraint backed by {@link TransactionDatePolicy}. */
@Documented
@Constraint(validatedBy = TransactionDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTransactionDate {

    String message() default "{transaction.date.outOfRange}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Whether null is accepted for fields whose value is optional. */
    boolean allowNull() default false;
}
