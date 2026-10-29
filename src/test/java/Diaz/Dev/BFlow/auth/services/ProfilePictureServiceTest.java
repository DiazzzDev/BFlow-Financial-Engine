package Diaz.Dev.BFlow.auth.services;

import bflow.auth.entities.User;
import bflow.auth.enums.PictureSource;
import bflow.auth.repository.RepositoryUser;
import bflow.auth.services.ProfilePictureService;
import bflow.auth.services.UserService;
import bflow.common.aws.service.ImageModerationService;
import bflow.common.aws.service.StorageService;
import bflow.common.exception.InvalidFileException;
import bflow.common.i18n.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilePictureServiceTest {

    @Mock private RepositoryUser users;
    @Mock private UserService userService;
    @Mock private StorageService storageService;
    @Mock private ImageModerationService moderationService;
    @Mock private MessageService messageService;

    private ProfilePictureService profilePictureService;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        profilePictureService = new ProfilePictureService(
                users, userService, storageService,
                moderationService, messageService);
        ReflectionTestUtils.setField(
                profilePictureService, "baseUrl", "https://api.example.test");
        ReflectionTestUtils.setField(
                profilePictureService, "maxFileSizeBytes", 2_097_152L);
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        when(userService.findById(userId)).thenReturn(user);
    }

    @Test
    void unsafeAvatarIsRejectedBeforeStorageOrProfileUpdate() {
        MockMultipartFile image = jpeg();
        doThrow(new InvalidFileException("unsafe image"))
                .when(moderationService)
                .assertSafeBytes(any(byte[].class), eq("image/jpeg"));

        assertThrows(InvalidFileException.class, () ->
                profilePictureService.updatePicture(userId, image));

        verify(storageService, never()).upload(
                anyString(), any(), anyLong(), anyString());
        verify(users, never()).save(any());
    }

    @Test
    void safeAvatarIsModeratedBeforeReplacingStoredPicture() {
        MockMultipartFile image = jpeg();

        String url = profilePictureService.updatePicture(userId, image);

        assertNotNull(url);
        verify(moderationService).assertSafeBytes(
                any(byte[].class), eq("image/jpeg"));
        verify(storageService).upload(
                eq("users/" + userId + "/avatar"), any(), eq(image.getSize()),
                eq("image/jpeg"));
        verify(users).save(user);
        org.junit.jupiter.api.Assertions.assertEquals(
                PictureSource.S3, user.getPictureSource());
    }

    @Test
    void avatarWithUnsupportedFilenameExtensionNeverReachesStorage() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "avatar.svg", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47,
                        0x0d, 0x0a, 0x1a, 0x0a});
        doThrow(new InvalidFileException("unsupported extension"))
                .when(moderationService)
                .validateFilename("avatar.svg", "image/png");

        assertThrows(InvalidFileException.class, () ->
                profilePictureService.updatePicture(userId, image));

        verify(storageService, never()).upload(
                anyString(), any(), anyLong(), anyString());
        verify(users, never()).save(any());
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
    }
}
