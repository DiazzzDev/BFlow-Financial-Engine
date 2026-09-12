package bflow.auth.services;

import bflow.auth.entities.User;
import bflow.auth.enums.PictureSource;
import bflow.auth.repository.RepositoryUser;
import bflow.common.aws.service.StorageObject;
import bflow.common.aws.service.StorageService;
import bflow.common.exception.InvalidFileException;
import bflow.common.exception.ResourceNotFoundException;
import bflow.common.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the profile picture lifecycle: uploading a new avatar to
 * object storage and serving it back to clients.
 *
 * <p>Every user is stored under a single, fixed object key
 * ({@code users/{userId}/avatar}) rather than one key per upload —
 * a new upload simply overwrites the previous object, so no cleanup
 * step or orphaned-file bookkeeping is needed. The MIME type is
 * preserved as S3 object metadata and echoed back on read, so the
 * key itself carries no extension.</p>
 *
 * <p>The bucket is fully private (see {@link StorageService}), so
 * {@link User#getPictureUrl()} for an S3-sourced picture is never a
 * direct S3 URL — it is this application's own
 * {@code GET /api/v1/users/{id}/picture} endpoint, which proxies the
 * bytes from storage. This keeps {@code pictureUrl} uniformly usable
 * by the frontend regardless of whether the picture originated from
 * Google or from an upload.</p>
 */
@Service
@RequiredArgsConstructor
public class ProfilePictureService {

    /** Content types accepted for a profile picture upload. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    /** Repository for user core data. */
    private final RepositoryUser userRepository;

    /** Service used to validate the requesting user's account state. */
    private final UserService userService;

    /** Storage abstraction used to persist and retrieve the avatar. */
    private final StorageService storageService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /** Public base URL this application is reachable at. */
    @Value("${app.base-url}")
    private String baseUrl;

    /** Maximum allowed avatar size in bytes, from configuration. */
    @Value("${app.storage.avatar-max-file-size-bytes}")
    private long maxFileSizeBytes;

    /**
     * Replaces the authenticated user's profile picture.
     *
     * @param userId the authenticated user's identifier
     * @param file the uploaded image
     * @return the picture URL now set on the user's profile
     * @throws InvalidFileException if the file is missing, too
     *         large, or not an accepted image type
     */
    @Transactional
    public String updatePicture(final UUID userId, final MultipartFile file) {

        userService.validateUserActive(userId);
        validateFile(file);

        User user = userService.findById(userId);
        String key = avatarKey(userId);

        try {
            storageService.upload(
                    key,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException ex) {
            throw new InvalidFileException(
                    messageService.get("file.readError")
            );
        }

        String pictureUrl = baseUrl + "/api/v1/users/" + userId
                + "/picture?v=" + java.time.Instant.now().toEpochMilli();

        user.setPictureUrl(pictureUrl);
        user.setPictureSource(PictureSource.S3);
        userRepository.save(user);

        return pictureUrl;
    }

    /**
     * Retrieves the raw bytes of a user's S3-stored profile picture.
     *
     * @param userId the owner of the picture
     * @return the picture content along with its content type
     * @throws ResourceNotFoundException if the user has no
     *         S3-stored picture
     */
    @Transactional(readOnly = true)
    public StorageObject getPicture(final UUID userId) {

        User user = userService.findById(userId);

        if (user.getPictureSource() != PictureSource.S3) {
            throw new ResourceNotFoundException(
                    messageService.get("user.picture.notFound")
            );
        }

        return storageService.download(avatarKey(userId));
    }

    /**
     * Builds the fixed, per-user object key an avatar is always
     * stored and overwritten under.
     *
     * @param userId the picture owner
     * @return the object key
     */
    private String avatarKey(final UUID userId) {
        return "users/" + userId + "/avatar";
    }

    /**
     * Validates that the uploaded file satisfies the application's
     * avatar constraints.
     *
     * @param file the uploaded file
     */
    private void validateFile(final MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException(messageService.get("file.missing"));
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException(
                    messageService.get("file.tooLarge", maxFileSizeBytes)
            );
        }

        String contentType = file.getContentType();

        if (!StringUtils.hasText(contentType)
                || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidFileException(
                    messageService.get(
                            "file.invalidContentType", contentType
                    )
            );
        }
    }
}
