package bflow.auth.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configuration for JWT decoding using JWKS.
 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    /** The base URL of the application for JWT JWKS endpoint. */
    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Configures the JwtDecoder bean.
     * @return the NimbusJwtDecoder instance.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
                .withJwkSetUri(
                        baseUrl + "/.well-known/jwks.json"
                )
                .build();
    }
}

