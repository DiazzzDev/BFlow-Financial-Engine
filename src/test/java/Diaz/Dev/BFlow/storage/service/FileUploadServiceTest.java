package Diaz.Dev.BFlow.storage.service;

import bflow.auth.entities.User;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.UserService;
import bflow.common.aws.service.StorageService;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests direct multipart storage used by the receipt shortcut endpoint. */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock private RepositoryStoredFile repositoryStoredFile;
    @Mock private RepositoryUser repositoryUser;
    @Mock private UserService userService;
    @Mock private StorageService storageService;
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
                storageService, s3Presigner, fileStatusTransitionService,
                messageService
        );
        ReflectionTestUtils.setField(service, "maxFileSizeBytes", 10_485_760L);
        ReflectionTestUtils.setField(
                service,
                "allowedContentTypes",
                Set.of("image/jpeg", "image/png", "image/webp",
                        "application/pdf")
        );
    }

    @Test
    void uploadDirectStoresTheFileAndMarksItReady() {
        User user = new User();
        user.setId(userId);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3}
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
    }
}
