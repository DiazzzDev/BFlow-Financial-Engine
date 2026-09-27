package bflow.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enriches request schemas with the Bean Validation constraints that
 * Springdoc already exposes structurally. This makes the same rules readable
 * directly in Swagger UI without duplicating validation logic in DTOs.
 */
@Configuration
public class ValidationDocumentationConfig {

    /**
     * Adds a concise validation summary to each request field description.
     *
     * @return OpenAPI customizer for request DTO schemas
     */
    @Bean
    public OpenApiCustomizer requestValidationDocumentationCustomizer() {
        return this::documentRequestValidations;
    }

    private void documentRequestValidations(final OpenAPI openApi) {
        if (openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {
            return;
        }

        openApi.getComponents().getSchemas().forEach((name, schema) -> {
            if (name.endsWith("Request")) {
                documentSchema(schema);
            }
        });
    }

    private void documentSchema(final Schema<?> requestSchema) {
        Map<String, Schema> properties = requestSchema.getProperties();
        if (properties == null || properties.isEmpty()) {
            return;
        }

        List<String> requiredProperties = requestSchema.getRequired();
        properties.forEach((propertyName, propertySchema) -> {
            List<String> validations = new ArrayList<>();
            if (requiredProperties != null
                    && requiredProperties.contains(propertyName)) {
                validations.add("required");
            }
            appendRange(validations, propertySchema);
            appendLength(validations, propertySchema);
            if (propertySchema.getPattern() != null) {
                validations.add("pattern: " + propertySchema.getPattern());
            }

            if (!validations.isEmpty()) {
                propertySchema.setDescription(appendValidationDescription(
                        propertySchema.getDescription(), validations
                ));
            }
        });
    }

    private void appendRange(
            final List<String> validations,
            final Schema<?> propertySchema
    ) {
        if (propertySchema.getMinimum() != null) {
            String prefix = Boolean.TRUE.equals(
                    propertySchema.getExclusiveMinimum())
                    ? "greater than " : "minimum ";
            validations.add(prefix + propertySchema.getMinimum());
        }
        if (propertySchema.getMaximum() != null) {
            String prefix = Boolean.TRUE.equals(
                    propertySchema.getExclusiveMaximum())
                    ? "less than " : "maximum ";
            validations.add(prefix + propertySchema.getMaximum());
        }
    }

    private void appendLength(
            final List<String> validations,
            final Schema<?> propertySchema
    ) {
        if (propertySchema.getMinLength() != null) {
            validations.add("minimum length " + propertySchema.getMinLength());
        }
        if (propertySchema.getMaxLength() != null) {
            validations.add("maximum length " + propertySchema.getMaxLength());
        }
    }

    private String appendValidationDescription(
            final String description,
            final List<String> validations
    ) {
        String validationSummary = "Validation: "
                + String.join("; ", validations) + ".";
        return description == null || description.isBlank()
                ? validationSummary : description + " " + validationSummary;
    }
}
