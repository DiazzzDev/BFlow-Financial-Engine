package bflow.common.i18n;

import bflow.auth.enums.SupportedLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Thin facade over {@link MessageSource} so callers resolve a message
 * key against the current request's locale without depending on
 * Spring's {@code MessageSource}/{@code LocaleContextHolder} APIs
 * directly.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    /** Underlying Spring message source. */
    private final MessageSource messageSource;

    /**
     * Resolves a message key for the current request's locale.
     *
     * @param code the message key (e.g. {@code category.name.required}).
     * @param args optional message arguments for placeholder substitution.
     * @return the resolved, localized message.
     */
    public String get(final String code, final Object... args) {
        return messageSource.getMessage(
                code, args, LocaleContextHolder.getLocale()
        );
    }

    /**
     * Resolves a message in a persisted user's preferred language. This is
     * intended for asynchronous notifications, where there is no recipient
     * request locale to use.
     *
     * @param code message bundle key
     * @param language recipient's preferred language; Spanish is the fallback
     * @param args optional placeholder values
     * @return the localized message
     */
    public String getForLanguage(
            final String code,
            final SupportedLanguage language,
            final Object... args
    ) {
        Locale locale = language == SupportedLanguage.EN
                ? Locale.ENGLISH : new Locale("es");
        return messageSource.getMessage(code, args, locale);
    }
}
