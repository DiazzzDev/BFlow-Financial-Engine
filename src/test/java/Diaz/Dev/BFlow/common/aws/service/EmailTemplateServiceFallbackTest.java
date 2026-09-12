package Diaz.Dev.BFlow.common.aws.service;

import bflow.auth.enums.SupportedLanguage;
import bflow.common.aws.service.EmailTemplateService;
import bflow.common.aws.service.SesEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Verifies that a missing Spanish translation falls back to the
 * English template instead of failing the email send — exercised
 * against a real {@link SpringTemplateEngine} and a deliberately
 * English-only fixture ({@code only-english.html}, no {@code es/}
 * counterpart), matching production's real resolver configuration
 * ({@code prefix=classpath:/templates/email/}).
 */
class EmailTemplateServiceFallbackTest {

    @Test
    void missingSpanishTemplateFallsBackToEnglish() {

        ClassLoaderTemplateResolver resolver =
                new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        MessageSource messageSource = new StaticMessageSource();

        EmailTemplateService service = new EmailTemplateService(
                templateEngine, mock(SesEmailService.class), messageSource
        );

        String html = ReflectionTestUtils.invokeMethod(
                service, "renderTemplate",
                "only-english", SupportedLanguage.ES, new Context()
        );

        assertTrue(
                html.contains("English-only fixture"),
                "Expected the English fallback content when no "
                        + "Spanish template exists"
        );
    }
}