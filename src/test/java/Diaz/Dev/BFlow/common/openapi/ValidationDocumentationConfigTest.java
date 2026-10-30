package Diaz.Dev.BFlow.common.openapi;

import bflow.common.openapi.ValidationDocumentationConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationDocumentationConfigTest {

    @Test
    void documentsValidationAndRequirednessForRequestSchemas() {
        Schema<String> required = new Schema<>();
        required.setDescription("The amount.");
        required.setMinimum(BigDecimal.ZERO);
        required.setExclusiveMinimum(true);
        required.setMaximum(BigDecimal.TEN);
        required.setExclusiveMaximum(false);
        Schema<String> optional = new Schema<>();
        optional.setMinLength(2);
        optional.setMaxLength(20);
        optional.setPattern("[a-z]+");
        Schema<String> noDescription = new Schema<>();
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("amount", required);
        properties.put("alias", optional);
        properties.put("notes", noDescription);
        Schema<Object> requestSchema = new Schema<>();
        requestSchema.setRequired(List.of("amount"));
        requestSchema.setProperties(properties);
        Schema<Object> unrelatedSchema = new Schema<>();
        unrelatedSchema.setProperties(Map.of("value", new Schema<>()));
        OpenAPI openApi = new OpenAPI().components(new Components()
                .schemas(Map.of(
                        "CreateRequest", requestSchema,
                        "Response", unrelatedSchema)));

        OpenApiCustomizer customizer =
                new ValidationDocumentationConfig()
                        .requestValidationDocumentationCustomizer();
        customizer.customise(openApi);

        assertFalse(required.getNullable());
        assertEquals(
                "The amount. Validation: required; greater than 0; maximum 10.",
                required.getDescription());
        assertTrue(optional.getDescription().contains(
                "optional; omit when unused"));
        assertTrue(optional.getDescription().contains("minimum length 2"));
        assertTrue(optional.getDescription().contains("maximum length 20"));
        assertTrue(optional.getDescription().contains("pattern: [a-z]+"));
        assertEquals(
                "Validation: optional; omit when unused.",
                noDescription.getDescription());
        assertNull(unrelatedSchema.getProperties().get("value")
                .getDescription());
    }

    @Test
    void safelyIgnoresMissingComponentsSchemasAndRequestProperties() {
        OpenApiCustomizer customizer =
                new ValidationDocumentationConfig()
                        .requestValidationDocumentationCustomizer();

        customizer.customise(new OpenAPI());
        customizer.customise(new OpenAPI().components(new Components()));
        customizer.customise(new OpenAPI().components(new Components()
                .schemas(Map.of(
                        "EmptyRequest", new Schema<>(),
                        "WithoutPropertiesRequest",
                        new Schema<>().properties(Map.of())))));
    }
}
