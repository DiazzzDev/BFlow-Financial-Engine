package bflow.common.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

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
}
