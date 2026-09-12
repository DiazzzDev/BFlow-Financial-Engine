package bflow.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Backend i18n infrastructure: message resolution and locale
 * negotiation from the {@code Accept-Language} header.
 *
 * <p>Spanish is the hard default at every fallback layer — an absent
 * header, an unsupported language, or a missing translation key all
 * resolve to Spanish rather than English or the JVM host's system
 * locale. This matches BFlow's primary market (El Salvador): the
 * current frontend does not send {@code Accept-Language} at all yet,
 * so without an explicit default here, every existing user would see
 * English messages by default.</p>
 */
@Configuration
public class LocaleConfig {

    /** Languages the backend currently has translations for. */
    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.ENGLISH, new Locale("es")
    );

    /**
     * Resolves messages from {@code messages(_en|_es).properties}.
     * Named "messageSource" so it also becomes the
     * {@code MessageSource} Spring Boot's Bean Validation
     * auto-configuration wires into the validator, letting
     * {@code @NotBlank(message = "{category.name.required}")}
     * resolve through these same files.
     *
     * @return the configured message source.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source =
                new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        // Never fall back to the JVM's host locale — only to the
        // base messages.properties (Spanish), which we control.
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    /**
     * Resolves the request locale from {@code Accept-Language},
     * restricted to {@link #SUPPORTED_LOCALES}, defaulting to
     * Spanish when the header is absent or names an unsupported
     * language — so requests from the current frontend (which sends
     * no such header) see BFlow's primary market language by default.
     *
     * @return the configured locale resolver.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver =
                new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(new Locale("es"));
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }
}