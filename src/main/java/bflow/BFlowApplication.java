package bflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the BFlow application.
 */
@EnableScheduling
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
        SpringApplication.run(BFlowApplication.class, args);
    }
}
