package bflow.common.aws.service;

import bflow.common.exception.InvalidFileException;
import bflow.common.exception.InvalidStorageKeyException;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.StorageException;
import bflow.common.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.InputStream;

/**
 * AWS S3 implementation of {@link StorageService}.
 *
 * <p>Objects are always stored privately (no ACL is ever set; the
 * bucket enforces BucketOwnerEnforced) and encrypted with SSE-S3.
 * Every key passed in is validated against the application's
 * trusted key namespace before any call reaches S3.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class S3StorageService implements StorageService {

    /** Prefix for objects owned by a specific user. */
    private static final String USERS_PREFIX = "users/";

    /** Prefix for temporary, not-yet-claimed objects. */
    private static final String TMP_PREFIX = "tmp/";

    /** HTTP status returned by S3 when an object is not found. */
    private static final int NOT_FOUND_STATUS = 404;

    /** AWS S3 client. */
    private final S3Client s3Client;

    /** Target S3 bucket, injected from configuration. */
    @Value("${aws.s3.bucket}")
    private String bucket;

    /** Maximum allowed upload size in bytes, from configuration. */
    @Value("${aws.s3.max-file-size-bytes}")
    private long maxFileSizeBytes;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void upload(
            final String key,
            final InputStream inputStream,
            final long contentLength,
            final String contentType
    ) {

        validateKey(key);
        validateFile(contentLength, contentType);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(inputStream, contentLength)
            );
        } catch (SdkException ex) {
            log.error(
                    "Failed to upload object '{}' to bucket '{}'",
                    key, bucket, ex
            );
            throw new StorageException(
                    messageService.get("file.storage.uploadFailed"), ex
            );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StorageObject download(final String key) {

        validateKey(key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try {
            ResponseInputStream<GetObjectResponse> object =
                    s3Client.getObject(request);

            GetObjectResponse response = object.response();

            long length = response.contentLength() != null
                    ? response.contentLength()
                    : 0L;

            return new StorageObject(
                    object,
                    response.contentType(),
                    length
            );
        } catch (NoSuchKeyException ex) {
            throw new ResourceNotFoundException(
                    messageService.get("file.storage.notFound", key)
            );
        } catch (SdkException ex) {
            log.error(
                    "Failed to download object '{}' from bucket '{}'",
                    key, bucket, ex
            );
            throw new StorageException(
                    messageService.get("file.storage.downloadFailed"), ex
            );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final String key) {

        validateKey(key);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException ex) {
            log.error(
                    "Failed to delete object '{}' from bucket '{}'",
                    key, bucket, ex
            );
            throw new StorageException(
                    messageService.get("file.storage.deleteFailed"), ex
            );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(final String key) {

        validateKey(key);

        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try {
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == NOT_FOUND_STATUS) {
                return false;
            }
            log.error(
                    "Failed to check existence of object '{}' in "
                            + "bucket '{}'",
                    key, bucket, ex
            );
            throw new StorageException(
                    messageService.get("file.storage.existsCheckFailed"), ex
            );
        } catch (SdkException ex) {
            log.error(
                    "Failed to check existence of object '{}' in "
                            + "bucket '{}'",
                    key, bucket, ex
            );
            throw new StorageException(
                    messageService.get("file.storage.existsCheckFailed"), ex
            );
        }
    }

    /**
     * Validates that a key is well-formed and scoped under the
     * application's trusted namespace. Keys are never trusted
     * as-is when they originate from client input.
     *
     * @param key the key to validate
     */
    private void validateKey(final String key) {

        if (!StringUtils.hasText(key)) {
            throw new InvalidStorageKeyException(
                    messageService.get("file.key.empty")
            );
        }

        if (key.startsWith("/") || key.contains("..")) {
            throw new InvalidStorageKeyException(
                    messageService.get("file.key.malformed", key)
            );
        }

        if (!key.startsWith(USERS_PREFIX)
                && !key.startsWith(TMP_PREFIX)) {
            throw new InvalidStorageKeyException(
                    messageService.get("file.key.invalidScope", key)
            );
        }
    }

    /**
     * Validates upload constraints that are independent of any
     * specific business rule (allowed MIME types are enforced at
     * the endpoint level).
     *
     * @param contentLength the size in bytes of the file to upload
     * @param contentType the MIME type of the file to upload
     */
    private void validateFile(
            final long contentLength,
            final String contentType
    ) {

        if (contentLength <= 0) {
            throw new InvalidFileException(
                    messageService.get("file.size.zero")
            );
        }

        if (contentLength > maxFileSizeBytes) {
            throw new InvalidFileException(
                    messageService.get("file.tooLarge", maxFileSizeBytes)
            );
        }

        if (!StringUtils.hasText(contentType)) {
            throw new InvalidFileException(
                    messageService.get("file.contentType.required")
            );
        }
    }
}
