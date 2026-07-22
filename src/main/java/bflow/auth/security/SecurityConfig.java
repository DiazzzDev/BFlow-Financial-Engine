package bflow.auth.security;

import bflow.auth.services.CurrentUserService;
import bflow.common.idempotency.config.IdempotencyProperties;
import bflow.common.idempotency.filter.IdempotencyFilter;
import bflow.common.idempotency.service.IdempotencyService;
import bflow.rate_limit.filter.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Main security configuration for the application.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {
    /** Filter responsible for applying request rate limiting. */
    private final RateLimitFilter rateLimitFilter;

    /** Service responsible for idempotency operations. */
    private final IdempotencyService idempotencyService;

    /** Configuration properties for idempotency handling. */
    private final IdempotencyProperties idempotencyProperties;

    /** Service used to resolve the authenticated application user. */
    private final CurrentUserService currentUserService;

    /** JSON serializer used by the idempotency filter. */
    private final ObjectMapper objectMapper;

    /**
     * Configures the security filter chain.
     * @param http the security object to configure.
     * @return the built filter chain.
     * @throws Exception if configuration fails.
     */
    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http)
            throws Exception {
        IdempotencyFilter idempotencyFilter = new IdempotencyFilter(
                idempotencyService, idempotencyProperties,
                currentUserService, objectMapper
        );

        return http
                 /*
                * CSRF protection is intentionally disabled.
                *
                * This application is a stateless REST API authenticated
                * exclusively through OAuth2 JWT Bearer tokens issued
                * by AWS Cognito.
                *
                * Since authentication does not rely on cookies or
                * HTTP sessions,
                * CSRF attacks are not applicable.
                */
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sess ->
                        sess.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write(
                        """
                                {
                                  "error": "unauthorized",
                                  "message": "Authentication required"
                                }
                            """);
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/actuator/info",
                                "/actuator/startup"
                        ).permitAll()
                        .requestMatchers("/api/v1/legal/**").permitAll()
                        .requestMatchers("/api/v1/webhooks/wompi").permitAll()
                        .requestMatchers(
                                "/api/auth/verify-email"
                        ).permitAll()
                        .requestMatchers("/api/test")
                        .authenticated()
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                )
                .addFilterAfter(rateLimitFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter,
                        BearerTokenAuthenticationFilter.class)
                .build();
    }
}
