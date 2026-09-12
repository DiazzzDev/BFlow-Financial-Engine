package bflow.storage.service;

import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.aws.service.StorageService;
import bflow.common.exception.FileAccessDeniedException;
import bflow.common.exception.InvalidFileException;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.i18n.MessageService;
import bflow.storage.DTO.FileResponse;
import bflow.storage.DTO.PresignedDownloadResponse;
import bflow.storage.DTO.PresignedUploadRequest;
import bflow.storage.DTO.PresignedUploadResponse;
import bflow.storage.entity.StoredFile;
import bflow.storage.enums.FileStatus;
import bflow.storage.repository.RepositoryStoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the lifecycle of user-uploaded files: issuing presigned S3
 * upload URLs, creating the corresponding {@link StoredFile} record
 * in {@code PENDING} status, and confirming completion once the
 * client has uploaded the object.
 *
 * <p>Only the file's declared metadata (name, content type, size)
 * is validated when the upload is initiated, since the request
 * never touches the actual bytes — the client uploads directly to
 * S3 using the presigned URL. Whether the object actually exists in
 * S3 is verified in {@link #completeUpload(UUID, UUID)}.</p>
 */
@Service
@RequiredArgsConstructor
public class FileUploadService {

    /** Prefix under which every user-owned object key is scoped. */
    private static final String USERS_PREFIX = "users/";

    /** Maximum length kept from a sanitized file extension. */
    private static final int MAX_EXTENSION_LENGTH = 10;

    /** Repository for stored file records. */
    private final RepositoryStoredFile repositoryStoredFile;

    /** Repository used to obtain a managed reference to the owner. */
    private final RepositoryUser repositoryUser;

    /** Service used to validate the requesting user's account state. */
    private final UserService userService;

    /** Storage abstraction used to verify object existence in S3. */
    private final StorageService storageService;

    /** Presigner used to issue the upload URL. */
    private final S3Presigner s3Presigner;

    /** Persists status transitions in their own transaction. */
    private final FileStatusTransitionService fileStatusTransitionService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /** Target S3 bucket, injected from configuration. */
    @Value("${aws.s3.bucket}")
    private String bucket;

    /** Maximum allowed upload size in bytes, from configuration. */
    @Value("${aws.s3.max-file-size-bytes}")
    private long maxFileSizeBytes;

    /** Content types accepted for upload, from configuration. */
    @Value("#{'${app.storage.allowed-content-types}'.split(',')}")
    private Set<String> allowedContentTypes;

    /** How long an issued presigned URL remains valid, in minutes. */
    @Value("${app.storage.presign-duration-minutes}")
    private long presignDurationMinutes;

    /** How long a presigned download URL remains valid, in minutes. */
    @Value("${app.storage.download-presign-duration-minutes}")
    private long downloadPresignDurationMinutes;

    /**
     * Creates a {@link StoredFile} record in {@code PENDING} status
     * and issues a presigned S3 URL the client can use to upload the
     * file's content directly.
     *
     * @param userId the authenticated user's identifier
     * @param request the declared file metadata
     * @return the presigned upload URL and related metadata
     * @throws InvalidFileException if the declared content type or
     *         size violates the application's upload constraints
     */
    @Transactional
    public PresignedUploadResponse createPresignedUpload(
            final UUID userId,
            final PresignedUploadRequest request
    ) {

        userService.validateUserActive(userId);
        validateContentType(request.getContentType());
        validateSize(request.getSizeBytes());

        String objectKey = generateKey(
                userId, request.getOriginalFilename()
        );

        StoredFile file = new StoredFile();
        file.setUser(repositoryUser.getReferenceById(userId));
        file.setObjectKey(objectKey);
        file.setOriginalFilename(request.getOriginalFilename());
        file.setContentType(request.getContentType());
        file.setSizeBytes(request.getSizeBytes());
        file.setStatus(FileStatus.PENDING);

        StoredFile saved = repositoryStoredFile.save(file);

        Duration signatureDuration =
                Duration.ofMinutes(presignDurationMinutes);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(request.getContentType())
                .contentLength(request.getSizeBytes())
                .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration)
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presigned =
                s3Presigner.presignPutObject(presignRequest);

        // Only surface the headers the client can and must actually
        // set (Content-Type). "host" and "content-length" are part
        // of the signature but are managed automatically by any
        // HTTP client/browser and cannot be overridden manually, so
        // exposing them here would only be misleading.
        Map<String, String> requiredHeaders = new LinkedHashMap<>();
        requiredHeaders.put("Content-Type", request.getContentType());

        return new PresignedUploadResponse(
                saved.getId(),
                presigned.url().toString(),
                objectKey,
                Instant.now().plus(signatureDuration),
                requiredHeaders
        );
    }

    /**
     * Confirms that a previously requested upload actually reached
     * S3 and transitions the file's status accordingly.
     *
     * <p>Idempotent for files already {@code UPLOADED}: repeated
     * calls simply return the current record without re-checking
     * S3. Files that already transitioned to {@code FAILED} are a
     * terminal state — the client must request a new presigned
     * upload rather than retry completion on the same record.</p>
     *
     * @param userId the authenticated user's identifier
     * @param fileId the stored file identifier
     * @return the file's current state once completion is confirmed
     * @throws FileAccessDeniedException if no such file exists for
     *         this user
     * @throws IllegalStateException if the file already failed
     * @throws ResourceNotFoundException if the object was not found
     *         in S3 (the file is transitioned to {@code FAILED}
     *         before this is thrown)
     */
    @Transactional(readOnly = true)
    public FileResponse completeUpload(
            final UUID userId,
            final UUID fileId
    ) {

        StoredFile file = repositoryStoredFile
                .findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new FileAccessDeniedException(
                        messageService.get("file.accessDenied")
                ));

        if (file.getStatus() == FileStatus.UPLOADED) {
            return toResponse(file);
        }

        if (file.getStatus() == FileStatus.FAILED) {
            throw new IllegalStateException(
                    messageService.get("file.upload.alreadyFailed")
            );
        }

        boolean uploaded = storageService.exists(file.getObjectKey());

        StoredFile updated = fileStatusTransitionService.transition(
                file.getId(),
                uploaded ? FileStatus.UPLOADED : FileStatus.FAILED
        );

        if (!uploaded) {
            throw new ResourceNotFoundException(
                    messageService.get("file.notFoundInStorage.retry")
            );
        }

        return toResponse(updated);
    }

    /**
     * Issues a presigned S3 download URL for a file the requesting
     * user owns.
     *
     * <p>Only files in {@code UPLOADED} status can be downloaded:
     * a {@code PENDING} file may not actually exist in S3 yet, and
     * a {@code FAILED} file never made it there.</p>
     *
     * @param userId the authenticated user's identifier
     * @param fileId the stored file identifier
     * @return the presigned download URL and file metadata
     * @throws FileAccessDeniedException if no such file exists for
     *         this user
     * @throws IllegalStateException if the file is not in
     *         {@code UPLOADED} status
     */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse createDownloadUrl(
            final UUID userId,
            final UUID fileId
    ) {

        StoredFile file = repositoryStoredFile
                .findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new FileAccessDeniedException(
                        messageService.get("file.accessDenied")
                ));

        if (file.getStatus() != FileStatus.UPLOADED) {
            throw new IllegalStateException(
                    messageService.get(
                            "file.download.notAvailable", file.getStatus()
                    )
            );
        }

        Duration signatureDuration = Duration.ofMinutes(
                downloadPresignDurationMinutes
        );

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(file.getObjectKey())
                .responseContentDisposition(
                        "attachment; filename=\""
                                + sanitizedForHeader(
                                file.getOriginalFilename()
                        )
                                + "\""
                )
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration)
                        .getObjectRequest(getObjectRequest)
                        .build();

        PresignedGetObjectRequest presigned =
                s3Presigner.presignGetObject(presignRequest);

        return new PresignedDownloadResponse(
                file.getId(),
                presigned.url().toString(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                Instant.now().plus(signatureDuration)
        );
    }

    /**
     * Validates that the declared content type is in the
     * application's allow-list.
     *
     * @param contentType the declared MIME type
     */
    private void validateContentType(final String contentType) {

        if (!allowedContentTypes.contains(contentType)) {
            throw new InvalidFileException(
                    messageService.get(
                            "file.contentType.notAllowed", contentType
                    )
            );
        }
    }

    /**
     * Validates that the declared size does not exceed the
     * application's configured maximum.
     *
     * @param sizeBytes the declared size in bytes
     */
    private void validateSize(final long sizeBytes) {

        if (sizeBytes > maxFileSizeBytes) {
            throw new InvalidFileException(
                    messageService.get("file.tooLarge", maxFileSizeBytes)
            );
        }
    }

    /**
     * Generates a new, unique object key scoped under the
     * requesting user's namespace, preserving the original file
     * extension when present.
     *
     * @param userId the owner's identifier
     * @param originalFilename the client-supplied file name
     * @return a key of the form {@code users/{userId}/{uuid}[.ext]}
     */
    private String generateKey(
            final UUID userId,
            final String originalFilename
    ) {

        String extension = sanitizedExtension(originalFilename);

        String base = USERS_PREFIX + userId + "/" + UUID.randomUUID();

        return extension.isEmpty() ? base : base + "." + extension;
    }

    /**
     * Extracts and sanitizes the file extension from a client
     * supplied file name, keeping only safe alphanumeric characters.
     *
     * @param originalFilename the client-supplied file name
     * @return the sanitized extension without the leading dot, or
     *         an empty string if none could be safely extracted
     */
    private String sanitizedExtension(final String originalFilename) {

        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }

        int dotIndex = originalFilename.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }

        String rawExtension = originalFilename
                .substring(dotIndex + 1)
                .toLowerCase();

        String sanitized = rawExtension.replaceAll("[^a-z0-9]", "");

        if (sanitized.length() > MAX_EXTENSION_LENGTH) {
            return sanitized.substring(0, MAX_EXTENSION_LENGTH);
        }

        return sanitized;
    }

    /**
     * Maps a {@link StoredFile} entity to its public representation.
     *
     * @param file the entity to map
     * @return the corresponding response DTO
     */
    private FileResponse toResponse(final StoredFile file) {
        return new FileResponse(
                file.getId(),
                file.getObjectKey(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getStatus(),
                file.getCreatedAt()
        );
    }

    /**
     * Strips characters that could break or inject into the
     * {@code Content-Disposition} response header (quotes, carriage
     * returns, newlines) from a client-supplied file name.
     *
     * @param originalFilename the client-supplied file name
     * @return a header-safe version of the file name
     */
    private String sanitizedForHeader(final String originalFilename) {

        if (!StringUtils.hasText(originalFilename)) {
            return "download";
        }

        return originalFilename.replaceAll("[\"\\r\\n]", "_");
    }
}
