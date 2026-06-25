package bflow.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configuration for Cognito JWT validation.
 */
@Configuration
public class CognitoJwtConfig {

    /**
     * Cognito issuer URI.
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * Creates a decoder for Cognito ID tokens.
     *
     * @return configured JWT decoder
     */
    @Bean("cognitoIdTokenDecoder")
    public JwtDecoder cognitoIdTokenDecoder() {
        String jwkSetUri = issuerUri + "/.well-known/jwks.json";
        return NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();
    }
}
