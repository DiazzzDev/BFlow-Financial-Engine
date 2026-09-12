package Diaz.Dev.BFlow.common.i18n;

import bflow.category.DTO.CategoryRequest;
import bflow.common.exception.GlobalExceptionHandler;
import bflow.common.i18n.LocaleConfig;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real production Accept-Language resolution
 * ({@link LocaleConfig#localeResolver()}) together with the real
 * Bean Validation message source and {@link GlobalExceptionHandler},
 * against a minimal echo controller — verifying the actual contract
 * end users depend on (a validation error's message text changes
 * with Accept-Language) without needing the full application
 * context, a database, or authentication.
 */
class LocaleEndToEndTest {

    /** Minimal controller: just enough to trigger @Valid binding. */
    @RestController
    static class EchoController {
        @PostMapping("/echo")
        public void echo(@Valid @RequestBody final CategoryRequest request) {
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(
                new LocaleConfig().messageSource()
        );
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new EchoController())
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setLocaleResolver(new LocaleConfig().localeResolver())
                .build();
    }

    @Test
    void englishHeaderReturnsEnglishMessage() throws Exception {
        mockMvc.perform(post("/echo")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("name: Category name is required."))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code")
                        .value("category.name.required"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Category name is required."));
    }

    @Test
    void spanishHeaderReturnsSpanishMessage() throws Exception {
        mockMvc.perform(post("/echo")
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "name: El nombre de la categoría es obligatorio."
                        ))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code")
                        .value("category.name.required"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("El nombre de la categoría es obligatorio."));
    }

    @Test
    void missingHeaderDefaultsToSpanish() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "name: El nombre de la categoría es obligatorio."
                        ));
    }

    @Test
    void unsupportedLanguageFallsBackToSpanish() throws Exception {
        mockMvc.perform(post("/echo")
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "name: El nombre de la categoría es obligatorio."
                        ));
    }

    @Test
    void validRequestPassesWithoutError() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Groceries\"}"))
                .andExpect(status().isOk());
    }
}
