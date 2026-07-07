package bflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application's Jackson ObjectMapper bean explicitly,
 * ensuring it is always available for components that need manual
 * JSON serialization outside of Spring MVC's request/response cycle
 * (e.g. servlet filters).
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates the application's shared ObjectMapper instance.
     *
     * @return configured ObjectMapper bean
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
