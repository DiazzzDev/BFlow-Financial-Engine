package Diaz.Dev.BFlow.storage.service;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.aws.service.StorageService;
import bflow.common.aws.service.StorageObject;
import bflow.common.aws.service.ImageModerationService;
import bflow.common.exception.InvalidFileException;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.i18n.MessageService;
import bflow.storage.entity.StoredFile;
import bflow.storage.enums.FileStatus;
import bflow.storage.repository.RepositoryStoredFile;
import bflow.storage.service.FileStatusTransitionService;
import bflow.storage.service.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** Tests direct multipart storage used by the receipt shortcut endpoint. */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock private RepositoryStoredFile repositoryStoredFile;
    @Mock private RepositoryUser repositoryUser;
    @Mock private UserService userService;
    @Mock private StorageService storageService;
    @Mock private ImageModerationService imageModerationService;
    @Mock private S3Presigner s3Presigner;
    @Mock private FileStatusTransitionService fileStatusTransitionService;
    @Mock private MessageService messageService;

    private FileUploadService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FileUploadService(
                repositoryStoredFile, repositoryUser, userService,
                storageService, imageModerationService, s3Presigner,
                fileStatusTransitionService,
                messageService
        );
        ReflectionTestUtils.setField(service, "maxFileSizeBytes", 10_485_760L);
        ReflectionTestUtils.setField(
                service,
                "allowedContentTypes",
                Set.of("image/jpeg", "image/png",
                        "application/pdf")
        );
    }

    @Test
    void uploadDirectStoresTheFileAndMarksItReady() {
        User user = new User();
        user.setId(userId);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}
        );
        when(repositoryUser.getReferenceById(userId)).thenReturn(user);
        when(repositoryStoredFile.save(any(StoredFile.class)))
                .thenAnswer(invocation -> {
                    StoredFile file = invocation.getArgument(0);
                    file.setId(fileId);
                    return file;
                });

        StoredFile stored = service.uploadDirect(userId, multipartFile);

        assertThat(stored.getId()).isEqualTo(fileId);
        assertThat(stored.getStatus()).isEqualTo(FileStatus.UPLOADED);
        assertThat(stored.getObjectKey())
                .startsWith("users/" + userId + "/");
        verify(userService).validateUserActive(userId);
        verify(storageService).upload(
                anyString(), any(InputStream.class), anyLong(),
                eq("image/jpeg")
        );
        verify(repositoryStoredFile).save(stored);
        verify(imageModerationService).assertSafeObject(
                org.mockito.ArgumentMatchers.startsWith(
                        "users/" + userId + "/"),
                eq("image/jpeg"), any(byte[].class));
    }

    @Test
    void uploadDirectDeletesUnsafeImageAndDoesNotCreateFileRecord() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
        org.mockito.Mockito.doThrow(new InvalidFileException("unsafe"))
                .when(imageModerationService)
                .assertSafeObject(anyString(), eq("image/jpeg"), any());

        assertThatThrownBy(() -> service.uploadDirect(userId, image))
                .isInstanceOf(InvalidFileException.class);

        verify(storageService).delete(
                org.mockito.ArgumentMatchers.startsWith(
                        "users/" + userId + "/"));
        verify(repositoryStoredFile, never()).save(any());
    }

    @Test
    void uploadDirectRejectsEmptyFileBeforeCallingStorage() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.uploadDirect(userId, empty))
                .isInstanceOf(InvalidFileException.class);

        verify(storageService, never()).upload(
                anyString(), any(InputStream.class), anyLong(), anyString());
        verify(repositoryStoredFile, never()).save(any());
    }

    @Test
    void uploadDirectRejectsUnsupportedTypeBeforeCallingStorage() {
        MockMultipartFile executable = new MockMultipartFile(
                "file", "payload.exe", "application/x-msdownload",
                new byte[] {1});

        assertThatThrownBy(() -> service.uploadDirect(userId, executable))
                .isInstanceOf(InvalidFileException.class);

        verify(storageService, never()).upload(
                anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void uploadDirectRejectsUnsupportedExtensionEvenWhenMimeIsAllowed() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "receipt.svg", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47,
                        0x0d, 0x0a, 0x1a, 0x0a});
        org.mockito.Mockito.doThrow(new InvalidFileException(
                "unsupported extension"))
                .when(imageModerationService)
                .validateFilename("receipt.svg", "image/png");

        assertThatThrownBy(() -> service.uploadDirect(userId, disguised))
                .isInstanceOf(InvalidFileException.class);

        verify(storageService, never()).upload(
                anyString(), any(InputStream.class), anyLong(), anyString());
        verify(repositoryStoredFile, never()).save(any());
    }

    @Test
    void uploadDirectRejectsFilesLargerThanConfiguredLimit() {
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg",
                new byte[10_485_761]);

        assertThatThrownBy(() -> service.uploadDirect(userId, oversized))
                .isInstanceOf(InvalidFileException.class);

        verify(storageService, never()).upload(
                anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void uploadDirectDeletesStoredObjectIfDatabaseSaveFails() {
        User user = new User();
        user.setId(userId);
        RuntimeException databaseFailure =
                new RuntimeException("database unavailable");
        MockMultipartFile upload = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});
        when(repositoryUser.getReferenceById(userId)).thenReturn(user);
        when(repositoryStoredFile.save(any(StoredFile.class)))
                .thenThrow(databaseFailure);

        assertThatThrownBy(() -> service.uploadDirect(userId, upload))
                .isSameAs(databaseFailure);

        org.mockito.ArgumentCaptor<String> key =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(
                key.capture(), any(InputStream.class), eq(5L), eq("image/jpeg"));
        verify(storageService).delete(key.getValue());
    }

    @Test
    void completeUploadMarksMissingObjectFailedBeforeReturningNotFound() {
        StoredFile pending = new StoredFile();
        pending.setId(fileId);
        pending.setObjectKey("users/" + userId + "/receipt.jpg");
        pending.setStatus(FileStatus.PENDING);
        when(repositoryStoredFile.findByIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(pending));
        when(storageService.exists(pending.getObjectKey())).thenReturn(false);

        assertThatThrownBy(() -> service.completeUpload(userId, fileId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(fileStatusTransitionService)
                .transition(fileId, FileStatus.FAILED);
    }

    @Test
    void completeUploadRejectsUnsafePresignedImageAndRemovesItFromStorage() {
        StoredFile pending = new StoredFile();
        pending.setId(fileId);
        pending.setObjectKey("users/" + userId + "/receipt.jpg");
        pending.setContentType("image/jpeg");
        pending.setStatus(FileStatus.PENDING);
        when(repositoryStoredFile.findByIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(pending));
        when(storageService.exists(pending.getObjectKey())).thenReturn(true);
        when(storageService.download(pending.getObjectKey()))
                .thenReturn(new StorageObject(
                        new ByteArrayInputStream(new byte[] {
                                (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1
                        }), "image/jpeg", 4));
        org.mockito.Mockito.doThrow(new InvalidFileException("unsafe"))
                .when(imageModerationService)
                .assertSafeObject(anyString(), eq("image/jpeg"), any());

        assertThatThrownBy(() -> service.completeUpload(userId, fileId))
                .isInstanceOf(InvalidFileException.class);

        verify(fileStatusTransitionService)
                .transition(fileId, FileStatus.FAILED);
        verify(storageService).delete(pending.getObjectKey());
    }

    @Test
    void completeUploadIsIdempotentForAlreadyUploadedFile() {
        StoredFile uploaded = new StoredFile();
        uploaded.setId(fileId);
        uploaded.setUser(new User());
        uploaded.setObjectKey("users/" + userId + "/receipt.jpg");
        uploaded.setOriginalFilename("receipt.jpg");
        uploaded.setContentType("image/jpeg");
        uploaded.setSizeBytes(3L);
        uploaded.setStatus(FileStatus.UPLOADED);
        when(repositoryStoredFile.findByIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(uploaded));

        var response = service.completeUpload(userId, fileId);

        assertThat(response).isNotNull();
        verify(storageService, never()).exists(anyString());
        verify(fileStatusTransitionService, never()).transition(any(), any());
    }

    @Test
    void completeUploadDoesNotMarkFileUploadedWhenStorageCheckFails() {
        StoredFile pending = new StoredFile();
        pending.setId(fileId);
        pending.setObjectKey("users/" + userId + "/receipt.jpg");
        pending.setStatus(FileStatus.PENDING);
        when(repositoryStoredFile.findByIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(pending));
        RuntimeException storageFailure = new RuntimeException("S3 unavailable");
        when(storageService.exists(pending.getObjectKey())).thenThrow(storageFailure);

        assertThatThrownBy(() -> service.completeUpload(userId, fileId))
                .isSameAs(storageFailure);

        verify(fileStatusTransitionService, never()).transition(any(), any());
    }

    @Test
    void downloadUrlIsRejectedWhileUploadIsPending() {
        StoredFile pending = new StoredFile();
        pending.setId(fileId);
        pending.setStatus(FileStatus.PENDING);
        when(repositoryStoredFile.findByIdAndUserId(fileId, userId))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.createDownloadUrl(userId, fileId))
                .isInstanceOf(IllegalStateException.class);

        verify(s3Presigner, never()).presignGetObject(any());
    }

    @Test
    void uploadDirectRejectsMimeAndSignatureMismatchBeforeStorage() {
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg",
                "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        org.mockito.Mockito.doThrow(new InvalidFileException("signature mismatch"))
                .when(imageModerationService)
                .validateFileSignature(any(byte[].class), eq("image/jpeg"));

        assertThatThrownBy(() -> service.uploadDirect(userId, spoofed))
                .isInstanceOf(InvalidFileException.class);

        verify(storageService, never()).upload(
                anyString(), any(InputStream.class), anyLong(), anyString());
        verify(repositoryStoredFile, never()).save(any());
    }
}
