package bflow.receipts.controllers;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.receipts.DTO.ReceiptConfirmRequest;
import bflow.receipts.DTO.ReceiptUploadRequest;
import bflow.receipts.DTO.ReceiptUploadResponse;
import bflow.receipts.service.ReceiptUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Receipts", description = "Upload and processing of receipts")
@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public final class ControllerReceiptUpload {

    /** Service responsible for receipt upload and processing operations. */
    private final ReceiptUploadService receiptUploadService;

    /** Service responsible for resolving the authenticated user. */
    private final CurrentUserService currentUserService;

    /** Service for resolving localized messages. */
    private final MessageService messageService;

    /**
     * Registers an uploaded photo as a receipt.
     * Camera-first flow: file already uploaded via the existing
     * presigned-upload endpoints; this is the only extra input the
     * user provides — everything else comes from OCR later.
     *
     * @param body request containing the uploaded receipt information
     * @param authentication authentication information of the current user
     * @param request HTTP request used to obtain the request URI
     * @return response containing the registered receipt information
     */
    @Operation(
            summary = "Registers an uploaded photo as a receipt.",
            description = "Registers an uploaded photo as a receipt. Camera-first "
                    + "flow: fileId is required and walletId is optional. When "
                    + "walletId is omitted, the caller's default OWNER wallet "
                    + "is selected."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ReceiptUploadResponse>> register(
            @Valid @RequestBody final ReceiptUploadRequest body,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        ReceiptUploadResponse response =
                receiptUploadService.register(userId, body);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        messageService.get("receipt.registered"),
                        response, request.getRequestURI()));
    }

    /**
     * Uploads a receipt file and registers it for OCR in one request.
     *
     * @param file receipt image or PDF to store and process
     * @param walletId optional destination wallet identifier
     * @param authentication authentication information of the current user
     * @param request HTTP request used to obtain the request URI
     * @return response containing the newly registered receipt
     */
    @Operation(
            summary = "Uploads and registers a receipt in one request.",
            description = "Camera-first multipart endpoint. file is required "
                    + "and is uploaded to private storage before OCR is "
                    + "queued. walletId is optional; when omitted, the "
                    + "caller's default OWNER wallet is selected."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Receipt registered and queued for OCR"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "File is missing, too large, or has an "
                            + "unsupported content type"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Caller cannot use the requested wallet")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReceiptUploadResponse>> uploadAndRegister(
            @Parameter(description = "Receipt image or PDF. Allowed content "
                    + "types and the configured multipart size limit apply.",
                    required = true)
            @RequestParam("file") final MultipartFile file,
            @Parameter(description = "Optional wallet UUID. The caller's "
                    + "default OWNER wallet is used when omitted.")
            @RequestParam(value = "walletId", required = false)
            final UUID walletId,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        ReceiptUploadResponse response = receiptUploadService
                .uploadAndRegister(userId, file, walletId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        messageService.get("receipt.registered"),
                        response, request.getRequestURI()));
    }

    /**
     * Retrieves the current processing status of a receipt.
     * Lets the frontend poll while OCR processes the receipt.
     *
     * @param id identifier of the receipt
     * @param authentication authentication information of the current user
     * @param request HTTP request used to obtain the request URI
     * @return response containing the current receipt processing status
     */
    @Operation(
            summary = "Retrieves the current processing status of a receipt.",
            description = "Retrieves the current processing status of a receipt. Lets the frontend poll while OCR processes the receipt."
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReceiptUploadResponse>> getStatus(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        ReceiptUploadResponse response =
                receiptUploadService.getStatus(userId, id);

        return ResponseEntity.ok(ApiResponse.success(
                messageService.get("receipt.status.retrieved"), response,
                request.getRequestURI()));
    }

    /**
     * Confirms a receipt's suggested data — as edited by the user —
     * into a new Expense or Income.
     *
     * @param id identifier of the receipt being confirmed
     * @param body the confirmed transaction data
     * @param authentication authentication information of the current user
     * @param request HTTP request used to obtain the request URI
     * @return response containing the confirmed receipt, now
     *         CONFIRMED and linked to the resulting Expense/Income
     */
    @Operation(
            summary = "Confirm a receipt draft as an Expense or Income.",
            description = "Creates the transaction from the values supplied "
                    + "by the client. title, type and a non-zero amount are "
                    + "required. categoryId is optional and creates an "
                    + "uncategorized transaction when omitted. date is optional "
                    + "and defaults to today in America/El_Salvador. Negative "
                    + "OCR amounts are accepted and stored as their positive "
                    + "magnitude. The receipt must be in EXTRACTED status."
    )
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<ReceiptUploadResponse>> confirm(
            @PathVariable final UUID id,
            @Valid @RequestBody final ReceiptConfirmRequest body,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        ReceiptUploadResponse response =
                receiptUploadService.confirm(userId, id, body);

        return ResponseEntity.ok(ApiResponse.success(
                messageService.get("receipt.confirmed"), response,
                request.getRequestURI()));
    }

    /**
     * Discards a receipt the user doesn't want to keep, deleting
     * its underlying file immediately.
     *
     * @param id identifier of the receipt being discarded
     * @param authentication authentication information of the current user
     * @param request HTTP request used to obtain the request URI
     * @return an empty response confirming the receipt was discarded
     */
    @Operation(
            summary = "Discards a receipt the user doesn't want to keep, deleting its underlying file immediately.",
            description = "Discards a receipt the user doesn't want to keep, deleting its underlying file immediately."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> discard(
            @PathVariable final UUID id,
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        receiptUploadService.discard(userId, id);

        return ResponseEntity.ok(ApiResponse.success(
                messageService.get("receipt.discarded"), null,
                request.getRequestURI()));
    }
}
