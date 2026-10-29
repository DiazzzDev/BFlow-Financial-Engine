package bflow.common.aws.service;

import bflow.common.exception.InvalidFileException;
import bflow.common.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.ModerationLabel;
import software.amazon.awssdk.services.rekognition.model.S3Object;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Blocks unsafe image uploads before application features expose them. */
@Service
@RequiredArgsConstructor
public class ImageModerationService {

    private static final float MIN_CONFIDENCE = 60.0f;
    private static final Set<String> BLOCKED_LABELS = Set.of(
            "explicit nudity", "suggestive", "violence",
            "visually disturbing", "rude gestures"
    );
    private static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf"
    );

    private final RekognitionClient rekognitionClient;
    private final MessageService messageService;

    @Value("${aws.s3.bucket}")
    private String bucket;

    /** Scans a small in-memory image, used for profile pictures. */
    public void assertSafeBytes(
            final byte[] bytes, final String contentType
    ) {
        String actualType = typeFromSignature(bytes);
        requireMatchingType(actualType, contentType);
        if ("application/pdf".equals(actualType)) {
            return;
        }
        requireSupportedImage(actualType);
        DetectModerationLabelsRequest request =
                DetectModerationLabelsRequest.builder()
                        .image(Image.builder()
                                .bytes(SdkBytes.fromByteArray(bytes))
                                .build())
                        .minConfidence(MIN_CONFIDENCE)
                        .build();
        rejectDetectedContent(rekognitionClient
                .detectModerationLabels(request).moderationLabels());
    }

    /** Validates the file signature and declared MIME type without scanning. */
    public void validateFileSignature(
            final byte[] signature, final String contentType
    ) {
        requireMatchingType(typeFromSignature(signature), contentType);
    }

    /** Rejects filenames whose extension contradicts the declared media type. */
    public void validateFilename(
            final String filename, final String contentType
    ) {
        if (filename == null || filename.isBlank()) {
            throw new InvalidFileException(
                    messageService.get("file.filename.required"));
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new InvalidFileException(
                    messageService.get("file.extension.notAllowed"));
        }
        String extension = filename.substring(dot + 1)
                .toLowerCase(Locale.ROOT);
        String expectedType = ALLOWED_EXTENSIONS.get(extension);
        if (expectedType == null || !expectedType.equalsIgnoreCase(contentType)) {
            throw new InvalidFileException(
                    messageService.get("file.extension.notAllowed"));
        }
    }

    /** Scans an image already uploaded privately to the configured bucket. */
    public void assertSafeObject(
            final String objectKey,
            final String contentType,
            final byte[] signature
    ) {
        String actualType = typeFromSignature(signature);
        requireMatchingType(actualType, contentType);
        if ("application/pdf".equals(actualType)) {
            return;
        }
        requireSupportedImage(actualType);
        DetectModerationLabelsRequest request =
                DetectModerationLabelsRequest.builder()
                        .image(Image.builder()
                                .s3Object(S3Object.builder()
                                        .bucket(bucket)
                                        .name(objectKey)
                                        .build())
                                .build())
                        .minConfidence(MIN_CONFIDENCE)
                        .build();
        rejectDetectedContent(rekognitionClient
                .detectModerationLabels(request).moderationLabels());
    }

    private void requireSupportedImage(final String contentType) {
        if (!"image/jpeg".equalsIgnoreCase(contentType)
                && !"image/png".equalsIgnoreCase(contentType)) {
            throw new InvalidFileException(
                    messageService.get("file.image.moderationUnsupported"));
        }
    }

    private void requireMatchingType(
            final String actualType, final String declaredType
    ) {
        if (!actualType.equalsIgnoreCase(declaredType)) {
            throw new InvalidFileException(
                    messageService.get("file.contentType.mismatch"));
        }
    }

    private String typeFromSignature(final byte[] bytes) {
        boolean png = bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4e
                && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
        boolean jpeg = bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff;
        boolean pdf = bytes.length >= 5
                && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46
                && bytes[4] == 0x2d;
        if (png) {
            return "image/png";
        }
        if (jpeg) {
            return "image/jpeg";
        }
        if (pdf) {
            return "application/pdf";
        }
        throw new InvalidFileException(
                messageService.get("file.contentType.mismatch"));
    }

    private void rejectDetectedContent(
            final java.util.List<ModerationLabel> labels
    ) {
        boolean unsafe = labels.stream().anyMatch(label ->
                isBlocked(label.name()) || isBlocked(label.parentName()));
        if (unsafe) {
            throw new InvalidFileException(
                    messageService.get("file.image.moderationRejected"));
        }
    }

    private boolean isBlocked(final String label) {
        return label != null
                && BLOCKED_LABELS.contains(label.toLowerCase(Locale.ROOT));
    }
}
