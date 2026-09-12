package Diaz.Dev.BFlow.common.i18n;

import bflow.common.i18n.LocaleConfig;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies locale resolution and message fallback without needing a
 * full Spring context — exercises the same {@link LocaleConfig} beans
 * directly.
 */
class LocaleConfigTest {

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        LocaleConfig config = new LocaleConfig();
        MessageSource messageSource = config.messageSource();
        messageService = new MessageService(messageSource);
    }

    @Test
    void resolvesEnglishMessage() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertEquals(
                "Category name is required.",
                messageService.get("category.name.required")
        );
    }

    @Test
    void resolvesSpanishMessage() {
        LocaleContextHolder.setLocale(new Locale("es"));
        assertEquals(
                "El nombre de la categoría es obligatorio.",
                messageService.get("category.name.required")
        );
    }

    @Test
    void fallsBackToSpanishForUnsupportedLocale() {
        // French has no messages_fr.properties — ResourceBundleMessageSource
        // falls back to the base messages.properties, which is now
        // Spanish content to match BFlow's default market language.
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertEquals(
                "El nombre de la categoría es obligatorio.",
                messageService.get("category.name.required")
        );
    }
}
