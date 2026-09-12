package bflow.legal.service;

import bflow.common.i18n.MessageService;
import bflow.legal.dto.LegalDocumentResponse;
import bflow.legal.enums.LegalDocumentType;
import bflow.legal.exception.LegalDocumentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public final class LegalServiceImpl implements LegalService {

    /** Base path for legal document resources. */
    private static final String BASE_PATH = "legal/";

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    @Override
    public LegalDocumentResponse getDocument(
            final String documentType,
            final String lang
    ) {

        validateLanguage(lang);

        LegalDocumentType type =
                LegalDocumentType.fromValue(documentType);

        String filename =
                type.getValue() + "_" + lang + ".md";

        String path = BASE_PATH + filename;

        try {
            ClassPathResource resource =
                    new ClassPathResource(path);

            if (!resource.exists()) {
                throw new LegalDocumentNotFoundException(
                        messageService.get("legal.document.notFound")
                );
            }

            String content;

            try (InputStream inputStream =
                         resource.getInputStream()) {

                content = new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            return buildResponse(
                    type.getValue(),
                    lang,
                    content
            );

        } catch (IOException e) {

            log.error(
                    "Error reading legal document: {}",
                    path,
                    e
            );

            throw new LegalDocumentNotFoundException(
                    messageService.get("legal.document.readError")
            );
        }
    }

    private void validateLanguage(final String lang) {

        if (!lang.equalsIgnoreCase("es")
                && !lang.equalsIgnoreCase("en")) {

            throw new IllegalArgumentException(
                    messageService.get("legal.language.unsupported")
            );
        }
    }

    private LegalDocumentResponse buildResponse(
            final String documentType,
            final String lang,
            final String content
    ) {

        LegalDocumentResponse response =
                new LegalDocumentResponse();

        response.setDocumentType(documentType);
        response.setLanguage(lang);
        response.setLastUpdated("2026-05-01");
        response.setContactEmail("support@bflow-studio.com");
        response.setContent(content);

        return response;
    }
}
