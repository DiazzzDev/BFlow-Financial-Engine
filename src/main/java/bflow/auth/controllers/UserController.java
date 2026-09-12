package bflow.auth.controllers;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.services.CurrentUserService;
import bflow.auth.services.ProfilePictureService;
import bflow.auth.services.UserService;
import bflow.common.aws.service.StorageObject;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for managing user profile operations.
 * Provides endpoints for retrieving, updating, and deleting user profiles.
 */
@Tag(name = "Users", description = "Profile, profile picture, and account deletion for the authenticated user")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public final class UserController {

    /** How long a served profile picture may be cached, in hours. */
    private static final long PICTURE_CACHE_HOURS = 1;

    /** Service for user profile operations. */
    private final UserService userService;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for profile picture upload and retrieval. */
    private final ProfilePictureService profilePictureService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Updates the current authenticated user's profile.
     * @param authentication the current user's authentication object.
     * @param requestBody the update profile request with new data.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing the updated user's profile.
     */
    @Operation(
            summary = "Updates the authenticated user's profile",
            description = "Modifies the editable profile fields (name, preferences, etc.) "
                    + "of the currently authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(
            final Authentication authentication,
            @Valid @RequestBody final UpdateUserProfileRequest requestBody,
            final HttpServletRequest request
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        UserProfileResponse updated =
                userService.updateProfile(userId, requestBody);

        return ApiResponse.success(
                messageService.get("user.profile.updated"),
                updated,
                request.getRequestURI()
        );
    }

    /**
     * Replaces the current authenticated user's profile picture.
     * @param authentication the current user's authentication object.
     * @param file the image to upload as the new profile picture.
     * @param request the HTTP request for path information.
     * @return a response containing the new picture URL.
     */
    @Operation(
            summary = "Replaces the user's profile picture",
            description = "Uploads a new image (multipart/form-data) and sets it as the "
                    + "authenticated user's profile picture, returning the resulting public URL."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile picture updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file or unsupported format")
    })
    @PatchMapping(
            value = "/me/picture",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<String> updatePicture(
            final Authentication authentication,
            @RequestParam("file") final MultipartFile file,
            final HttpServletRequest request
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        String pictureUrl =
                profilePictureService.updatePicture(userId, file);

        return ApiResponse.success(
                messageService.get("user.picture.updated"),
                pictureUrl,
                request.getRequestURI()
        );
    }

    /**
     * Serves a user's profile picture. Public — a profile picture is
     * not sensitive data, and a plain {@code <img>} tag cannot attach
     * an Authorization header, the same reason Google's own picture
     * URLs are publicly reachable.
     * @param userId the picture owner's identifier.
     * @return the raw image bytes with their original content type.
     */
    @Operation(
            summary = "Gets a user's profile picture",
            description = "Public endpoint (no authentication) that serves the image bytes "
                    + "for the given user's profile picture, cached for 1 hour. It is public "
                    + "because a plain <img> tag cannot send an Authorization header."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image served successfully")
    @GetMapping("/{userId}/picture")
    public ResponseEntity<InputStreamResource> getPicture(
            @PathVariable final UUID userId
    ) {

        StorageObject picture = profilePictureService.getPicture(userId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        picture.contentType()
                ))
                .contentLength(picture.contentLength())
                .cacheControl(CacheControl
                        .maxAge(PICTURE_CACHE_HOURS, TimeUnit.HOURS)
                        .cachePublic()
                )
                .body(new InputStreamResource(picture.content()));
    }

    /**
     * Deletes the current authenticated user's account (soft delete).
     * @param authentication the current user's authentication object.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing a success response.
     */
    @Operation(
            summary = "Deletes (soft delete) the authenticated user's account",
            description = "Marks the current user's account as deleted without physically "
                    + "removing their data from the database."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(
            final Authentication authentication,
            final HttpServletRequest request
    ) {

        UUID userId = currentUserService.getCurrentUserId(authentication);

        userService.softDelete(userId);

        return ApiResponse.success(
                messageService.get("user.account.deleted"),
                null,
                request.getRequestURI()
        );
    }

}
