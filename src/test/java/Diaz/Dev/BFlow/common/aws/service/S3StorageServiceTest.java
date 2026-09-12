package Diaz.Dev.BFlow.common.aws.service;

import bflow.common.aws.service.S3StorageService;
import bflow.common.aws.service.StorageObject;
import bflow.common.exception.InvalidFileException;
import bflow.common.exception.InvalidStorageKeyException;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.exception.StorageException;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    private static final long MAX_FILE_SIZE_BYTES = 10_485_760L;
    private static final String VALID_KEY = "users/00000000-0000-0000-0000-000000000001/doc.pdf";
    private static final String TMP_KEY = "tmp/11111111-1111-1111-1111-111111111111";

    @Mock private S3Client s3Client;

    @Mock private MessageService messageService;

    private S3StorageService service;

    @BeforeEach
    void setUp() {
        service = new S3StorageService(s3Client, messageService);
        ReflectionTestUtils.setField(service, "bucket", "bflow-files-dev");
        ReflectionTestUtils.setField(
                service, "maxFileSizeBytes", MAX_FILE_SIZE_BYTES);
    }

    private InputStream content() {
        return new ByteArrayInputStream("hello".getBytes());
    }

    @Test
    void uploadStoresObjectWhenKeyAndFileAreValid() {
        when(s3Client.putObject(
                any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        service.upload(VALID_KEY, content(), 5, "application/pdf");

        verify(s3Client).putObject(
                any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadAcceptsTmpPrefixedKeys() {
        when(s3Client.putObject(
                any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        service.upload(TMP_KEY, content(), 5, "application/pdf");

        verify(s3Client).putObject(
                any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadRejectsKeyOutsideTrustedNamespace() {
        assertThatThrownBy(() ->
                service.upload("invoices/doc.pdf", content(), 5, "application/pdf"))
                .isInstanceOf(InvalidStorageKeyException.class);

        verify(s3Client, never()).putObject(
                any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadRejectsPathTraversalKey() {
        assertThatThrownBy(() ->
                service.upload("users/../etc/passwd", content(), 5, "application/pdf"))
                .isInstanceOf(InvalidStorageKeyException.class);
    }

    @Test
    void uploadRejectsBlankKey() {
        assertThatThrownBy(() ->
                service.upload("   ", content(), 5, "application/pdf"))
                .isInstanceOf(InvalidStorageKeyException.class);
    }

    @Test
    void uploadRejectsNonPositiveContentLength() {
        assertThatThrownBy(() ->
                service.upload(VALID_KEY, content(), 0, "application/pdf"))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void uploadRejectsFileExceedingMaxSize() {
        assertThatThrownBy(() ->
                service.upload(
                        VALID_KEY, content(), MAX_FILE_SIZE_BYTES + 1, "application/pdf"))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void uploadRejectsBlankContentType() {
        assertThatThrownBy(() ->
                service.upload(VALID_KEY, content(), 5, " "))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void uploadWrapsSdkFailureAsStorageException() {
        when(s3Client.putObject(
                any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() ->
                service.upload(VALID_KEY, content(), 5, "application/pdf"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void downloadReturnsStoredObjectMetadata() {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentType("application/pdf")
                .contentLength(5L)
                .build();

        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(response, content());

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStream);

        StorageObject result = service.download(VALID_KEY);

        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.contentLength()).isEqualTo(5L);
    }

    @Test
    void downloadThrowsResourceNotFoundWhenKeyMissing() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> service.download(VALID_KEY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadWrapsOtherSdkFailuresAsStorageException() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> service.download(VALID_KEY))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void downloadRejectsUntrustedKey() {
        assertThatThrownBy(() -> service.download("invoices/doc.pdf"))
                .isInstanceOf(InvalidStorageKeyException.class);
    }

    @Test
    void deleteRemovesObjectForValidKey() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        service.delete(VALID_KEY);

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteRejectsUntrustedKey() {
        assertThatThrownBy(() -> service.delete("invoices/doc.pdf"))
                .isInstanceOf(InvalidStorageKeyException.class);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteWrapsSdkFailureAsStorageException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> service.delete(VALID_KEY))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void existsReturnsTrueWhenHeadObjectSucceeds() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(service.exists(VALID_KEY)).isTrue();
    }

    @Test
    void existsReturnsFalseWhenNoSuchKey() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThat(service.exists(VALID_KEY)).isFalse();
    }

    @Test
    void existsReturnsFalseWhenS3RespondsNotFound() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder()
                        .statusCode(404)
                        .build());

        assertThat(service.exists(VALID_KEY)).isFalse();
    }

    @Test
    void existsWrapsOtherS3FailuresAsStorageException() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder()
                        .statusCode(500)
                        .build());

        assertThatThrownBy(() -> service.exists(VALID_KEY))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void existsRejectsUntrustedKey() {
        assertThatThrownBy(() -> service.exists("invoices/doc.pdf"))
                .isInstanceOf(InvalidStorageKeyException.class);
    }
}
