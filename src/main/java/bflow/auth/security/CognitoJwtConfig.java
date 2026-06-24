package bflow.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class CognitoJwtConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean("cognitoIdTokenDecoder")
    public JwtDecoder cognitoIdTokenDecoder() {
        String jwkSetUri = issuerUri + "/.well-known/jwks.json";
        return NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();
    }
}