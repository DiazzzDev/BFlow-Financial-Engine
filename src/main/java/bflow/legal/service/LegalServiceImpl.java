package bflow.legal.service;

import bflow.legal.DTO.LegalDocumentResponse;
import bflow.legal.enums.LegalDocumentType;
import bflow.legal.exception.LegalDocumentNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class LegalServiceImpl implements LegalService {

    private static final String BASE_PATH = "legal/";

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
                        "Legal document not found"
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
                    "Unable to read legal document"
            );
        }
    }

    private void validateLanguage(final String lang) {

        if (!lang.equalsIgnoreCase("es")
                && !lang.equalsIgnoreCase("en")) {

            throw new IllegalArgumentException(
                    "Unsupported language"
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