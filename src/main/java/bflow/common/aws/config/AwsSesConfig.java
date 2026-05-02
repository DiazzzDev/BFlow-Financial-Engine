package bflow.common.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * AWS SES configuration class.
 * Provides bean configuration for AWS SES email service.
 */
@Configuration
public class AwsSesConfig {

    /**
     * AWS region for SES service.
     */
    @Value("${aws.region}")
    private String region;

    /**
     * Create and configure SES client bean.
     *
     * @return configured SesClient instance
     */
    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
                .region(Region.of(region))
                .build();
    }
}
