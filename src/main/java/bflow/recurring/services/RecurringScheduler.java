package bflow.recurring.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service for executing recurring transactions.
 */
@Service
@RequiredArgsConstructor
public class RecurringScheduler {
    /**
     * Service for executing recurring transactions.
     */
    private final RecurringExecutionService recurringExecutionService;

    /**
     * Execute due recurring transactions every 10 seconds.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void runRecurring() {
        recurringExecutionService.executeDueTransactions();
    }
}
