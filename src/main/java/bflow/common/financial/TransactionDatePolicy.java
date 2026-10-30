package bflow.common.financial;

import bflow.common.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Single business-calendar policy for user-provided transaction dates. */
@Component
@RequiredArgsConstructor
public class TransactionDatePolicy {

    private static final int MAX_HISTORY_YEARS = 50;

    private final Clock businessClock;
    private final MessageService messageService;

    /** @return current date in the configured business time zone */
    public LocalDate today() {
        return LocalDate.now(businessClock);
    }

    /**
     * Checks a transaction date against the inclusive business-date window.
     *
     * @param date transaction date
     * @return whether the date is between 50 years ago and today, inclusive
     */
    public boolean isValid(final LocalDate date) {
        if (date == null) {
            return false;
        }
        LocalDate today = today();
        LocalDate earliest = today.minusYears(MAX_HISTORY_YEARS);
        return !date.isBefore(earliest) && !date.isAfter(today);
    }

    /**
     * Rejects a missing or out-of-range date with a localized explanation.
     *
     * @param date transaction date
     */
    public void validateRequired(final LocalDate date) {
        if (date == null) {
            throw new InvalidTransactionDateException(
                    messageService.get("transaction.date.required")
            );
        }
        if (!isValid(date)) {
            throw invalidDate();
        }
    }

    /**
     * Validates an optional date when present.
     *
     * @param date optional transaction date
     */
    public void validateOptional(final LocalDate date) {
        if (date != null && !isValid(date)) {
            throw invalidDate();
        }
    }

    private InvalidTransactionDateException invalidDate() {
        LocalDate today = today();
        LocalDate earliest = today.minusYears(MAX_HISTORY_YEARS);
        return new InvalidTransactionDateException(messageService.get(
                "transaction.date.outOfRangeWithBounds", earliest, today
        ));
    }
}
