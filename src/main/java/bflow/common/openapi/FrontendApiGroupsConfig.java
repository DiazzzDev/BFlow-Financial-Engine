package bflow.common.openapi;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds a compact OpenAPI group for the app's day-to-day transaction flows. */
@Configuration
public class FrontendApiGroupsConfig {

    /**
     * Keeps transaction creation, receipt capture and file upload schemas
     * together so frontend developers can inspect the primary input flows
     * without navigating the complete API catalog.
     *
     * @return the frontend transaction API group
     */
    @Bean
    public GroupedOpenApi frontendTransactionsApi() {
        return GroupedOpenApi.builder()
                .group("frontend-transactions")
                .pathsToMatch(
                        "/api/v1/expenses/**",
                        "/api/v1/incomes/**",
                        "/api/v1/recurring/**",
                        "/api/v1/receipts/**",
                        "/api/v1/quick-expenses/**",
                        "/api/v1/files/**")
                .build();
    }
}
