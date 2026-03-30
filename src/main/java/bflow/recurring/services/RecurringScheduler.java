package bflow.recurring.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecurringScheduler {
    private final RecurringExecutionService recurringExecutionService;

    //Every minute
    @Scheduled(cron = "*/10 * * * * *")
    public void runRecurring() {
        recurringExecutionService.executeDueTransactions();
    }
}
