package bflow.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Provides the business-calendar clock used for date-only operations. */
@Configuration
public class TimeConfiguration {

    /** Canonical business zone for BFlow calendar dates. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/El_Salvador");

    /**
     * Supplies a clock in the business zone instead of relying on the JVM
     * default zone, which varies between local, container and CI runtimes.
     *
     * @return El Salvador business-calendar clock
     */
    @Bean
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
