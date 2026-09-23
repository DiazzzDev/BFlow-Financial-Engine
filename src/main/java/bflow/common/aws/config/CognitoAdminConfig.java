package bflow.common.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * AWS Cognito admin configuration class.
 * Provides the client used for administrative operations (account
 * hard deletion) against the user pool. Distinct from the JWT
 * validation path, which only reads the issuer's public keys.
 */
@Configuration
public class CognitoAdminConfig {

    /**
     * AWS region for the Cognito service.
     */
    @Value("${aws.region}")
    private String region;

    /**
     * Create and configure the Cognito Identity Provider admin
     * client. Uses the AWS default credential provider chain; no
     * static credentials are configured here.
     *
     * @return configured CognitoIdentityProviderClient instance
     */
    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }
}