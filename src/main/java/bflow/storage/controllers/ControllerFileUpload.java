package bflow.storage.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.storage.DTO.FileResponse;
import bflow.storage.DTO.PresignedDownloadResponse;
import bflow.storage.DTO.PresignedUploadRequest;
import bflow.storage.DTO.PresignedUploadResponse;
import bflow.storage.service.FileUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for the file upload lifecycle.
 * Provides REST endpoints for requesting presigned S3 upload URLs
 * and confirming upload completion.
 */
@Tag(name = "Storage", description = "Upload and management of generic files")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public final class ControllerFileUpload {

    /** The service handling file upload business logic. */
    private final FileUploadService fileUploadService;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Requests a presigned S3 upload URL for a new file. Creates the
     * corresponding {@code StoredFile} record in {@code PENDING}
     * status.
     *
     * @param body the declared file metadata.
     * @param authentication the authenticated user's principal.
     * @param request the HTTP request for path information.
     * @return a standard API response containing the presigned
     *         upload URL and related metadata.
     */
    @Operation(
            summary = "Requests a presigned S3 upload URL for a new file.",
            description = "Requests a presigned S3 upload URL for a new file. Creates the corresponding StoredFile record in PENDING status."
    )
    @PostMapping("/presigned-upload")
    public ResponseEntity<ApiResponse<PresignedUploadResponse>>
    createPresignedUpload(
            @Valid @RequestBody final PresignedUploadRequest body,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        PresignedUploadResponse response = fileUploadService
                .createPresignedUpload(userId, body);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        messageService.get("file.presignedUpload.generated"),
                        response,
                        request.getRequestURI()
                ));
    }

    /**
     * Confirms that a previously requested upload actually reached
     * S3, transitioning the file's status accordingly.
     *
     * @param id the stored file identifier.
     * @param authentication the authenticated user's principal.
     * @param request the HTTP request for path information.
     * @return a standard API response containing the file's current
     *         state.
     */
    @Operation(
            summary = "Confirms that a previously requested upload actually reached S3, transitioning the file's status accordingly.",
            description = "Confirms that a previously requested upload actually reached S3, transitioning the file's status accordingly."
    )
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<FileResponse>> completeUpload(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        FileResponse response = fileUploadService
                .completeUpload(userId, id);

        return ResponseEntity.ok(ApiResponse.success(
                messageService.get("file.upload.completed"),
                response,
                request.getRequestURI()
        ));
    }

    /**
     * Issues a presigned S3 download URL for a file the
     * authenticated user owns.
     *
     * @param id the stored file identifier.
     * @param authentication the authenticated user's principal.
     * @param request the HTTP request for path information.
     * @return a standard API response containing the presigned
     *         download URL and file metadata.
     */
    @Operation(
            summary = "Issues a presigned S3 download URL for a file the authenticated user owns.",
            description = "Issues a presigned S3 download URL for a file the authenticated user owns."
    )
    @GetMapping("/{id}/download")
    public ResponseEntity<ApiResponse<PresignedDownloadResponse>>
    createDownloadUrl(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);

        PresignedDownloadResponse response = fileUploadService
                .createDownloadUrl(userId, id);

        return ResponseEntity.ok(ApiResponse.success(
                messageService.get("file.presignedDownload.generated"),
                response,
                request.getRequestURI()
        ));
    }
}
