package bflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.scheduling.annotation.EnableScheduling;
import bflow.common.idempotency.config.IdempotencyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main entry point for the BFlow application.
 */
@EnableScheduling
@EnableConfigurationProperties(IdempotencyProperties.class)
@SpringBootApplication
public final class BFlowApplication {
    /**
     * Private constructor to prevent instantiation.
     */
    private BFlowApplication() {
    }

    /**
     * Main method to launch the Spring Boot application.
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        final int capacity = 2048;

        SpringApplication app =
                new SpringApplication(BFlowApplication.class);

        app.setApplicationStartup(
                new BufferingApplicationStartup(capacity)
        );

        app.run(args);
    }
}
