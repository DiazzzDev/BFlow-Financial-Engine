package bflow.common.openapi;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes the complete versioned API as a single frontend-facing catalog. */
@Configuration
public class FrontendApiGroupsConfig {

    /**
     * Includes every versioned API endpoint so frontend developers can inspect
     * the full contract from one Swagger UI group.
     *
     * @return the complete frontend API group
     */
    @Bean
    public GroupedOpenApi frontendApi() {
        return GroupedOpenApi.builder()
                .group("frontend-api")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
