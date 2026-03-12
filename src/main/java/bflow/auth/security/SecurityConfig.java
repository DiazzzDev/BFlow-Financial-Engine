package bflow.auth.security;

import bflow.auth.security.jwt.JwtAuthenticationFilter;
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
    /** Filter for JWT validation. */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    /** Handler for OAuth2 success events. */
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    /**
     * Configures the security filter chain.
     * @param http the security object to configure.
     * @return the built filter chain.
     * @throws Exception if configuration fails.
     */
    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http)
            throws Exception {
        return http
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
                            res.getWriter().write("""
                        {
                          "error": "unauthorized",
                          "message": "Authentication required"
                        }
                    """);
                        })
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login", "/api/auth/register",
                            "/api/auth/refresh", "/.well-known/jwks.json")
                        .permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/actuator/health",
                                "/v3/api-docs/**", "/actuator/info"
                        ).permitAll()
                    .requestMatchers("/login/oauth2/**", "/oauth2/**")
                        .permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 ->
                        oauth2.successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
