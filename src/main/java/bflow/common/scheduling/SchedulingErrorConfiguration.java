package bflow.common.scheduling;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import javax.sql.DataSource;

/**
 * Registers {@link TransientAwareErrorHandler} as the error handler
 * for ALL {@code @Scheduled} tasks application-wide — a single
 * registration point, not a per-scheduler change. This applies to
 * every existing scheduler (RecurringScheduler,
 * IdempotencyCleanupTask, StoredFileCleanupTask,
 * SubscriptionRenewalScheduler) and any future one, automatically.
 *
 * <p>{@code ScheduledTaskRegistrar} itself has no error-handler
 * setter — the handler lives on the {@link ThreadPoolTaskScheduler}
 * that actually runs the tasks, so a custom scheduler is built here
 * with the handler wired in, then registered via
 * {@link ScheduledTaskRegistrar#setTaskScheduler}.
 */
@Configuration
@RequiredArgsConstructor
@EnableSchedulerLock(defaultLockAtMostFor = "PT1H")
public class SchedulingErrorConfiguration implements SchedulingConfigurer {

    /** Default thread pool size for the scheduled-task executor. */
    private static final int SCHEDULER_POOL_SIZE = 5;

    /**
     * Custom error handler injected to manage exceptions in scheduled tasks.
     */
    private final TransientAwareErrorHandler errorHandler;

    /** Provides a PostgreSQL-backed lock shared by every application replica. */
    @Bean
    public LockProvider lockProvider(final DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }

    @Override
    public final void configureTasks(final ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setErrorHandler(errorHandler);
        scheduler.initialize();

        registrar.setTaskScheduler(scheduler);
    }
}
