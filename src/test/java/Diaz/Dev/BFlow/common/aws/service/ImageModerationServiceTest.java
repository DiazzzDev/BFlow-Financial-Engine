package Diaz.Dev.BFlow.common.aws.service;

import bflow.common.exception.InvalidFileException;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.ModerationLabel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageModerationServiceTest {

    private static final byte[] JPEG_SIGNATURE = {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00
    };

    @Mock private RekognitionClient rekognitionClient;
    @Mock private MessageService messageService;

    private ImageModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ImageModerationService(
                rekognitionClient, messageService);
        ReflectionTestUtils.setField(
                moderationService, "bucket", "bflow-private-test");
    }

    @Test
    void allowsImageWhenModerationReturnsNoUnsafeLabels() {
        when(rekognitionClient.detectModerationLabels(
                any(software.amazon.awssdk.services.rekognition.model
                        .DetectModerationLabelsRequest.class)))
                .thenReturn(DetectModerationLabelsResponse.builder()
                        .moderationLabels(List.of()).build());

        moderationService.assertSafeBytes(JPEG_SIGNATURE, "image/jpeg");

        verify(rekognitionClient).detectModerationLabels(any());
    }

    @Test
    void rejectsExplicitNudityAndViolenceLabels() {
        when(rekognitionClient.detectModerationLabels(any(
                software.amazon.awssdk.services.rekognition.model
                        .DetectModerationLabelsRequest.class)))
                .thenReturn(DetectModerationLabelsResponse.builder()
                        .moderationLabels(ModerationLabel.builder()
                                .name("Violence")
                                .confidence(98.0f)
                                .build())
                        .build());

        assertThrows(InvalidFileException.class, () ->
                moderationService.assertSafeBytes(
                        JPEG_SIGNATURE, "image/jpeg"));
    }

    @Test
    void rejectsSpoofedMimeTypeBeforeCallingRekognition() {
        assertThrows(InvalidFileException.class, () ->
                moderationService.assertSafeBytes(
                        JPEG_SIGNATURE, "image/png"));

        verify(rekognitionClient, never()).detectModerationLabels(any(
                software.amazon.awssdk.services.rekognition.model
                        .DetectModerationLabelsRequest.class));
    }

    @Test
    void rejectsUnsupportedOrMismatchedFilenameExtension() {
        assertThrows(InvalidFileException.class,
                () -> moderationService.validateFilename(
                        "receipt.svg", "image/png"));
        assertThrows(InvalidFileException.class,
                () -> moderationService.validateFilename(
                        "receipt.pdf", "image/png"));
        verifyNoInteractions(rekognitionClient);
    }

    @Test
    void acceptsOnlyMatchingReceiptAndAvatarExtensions() {
        moderationService.validateFilename("receipt.JPEG", "image/jpeg");
        moderationService.validateFilename("receipt.png", "image/png");
        moderationService.validateFilename("receipt.pdf", "application/pdf");
    }

    @Test
    void scansPrivateS3ObjectUsingConfiguredBucketAndObjectKey() {
        when(rekognitionClient.detectModerationLabels(any(
                software.amazon.awssdk.services.rekognition.model
                        .DetectModerationLabelsRequest.class)))
                .thenReturn(DetectModerationLabelsResponse.builder()
                        .moderationLabels(List.of()).build());

        moderationService.assertSafeObject(
                "users/user-id/receipt.png", "image/jpeg", JPEG_SIGNATURE);

        verify(rekognitionClient).detectModerationLabels(any());
    }

    @Test
    void leavesPdfForReceiptOcrButRejectsWrongSignature() {
        moderationService.assertSafeBytes(
                new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d},
                "application/pdf");

        verify(rekognitionClient, never()).detectModerationLabels(any(
                software.amazon.awssdk.services.rekognition.model
                        .DetectModerationLabelsRequest.class));
    }
}
