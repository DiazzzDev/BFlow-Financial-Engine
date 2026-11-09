package bflow.gmail.controller;

import bflow.auth.services.CurrentUserService;
import bflow.common.i18n.MessageService;
import bflow.common.response.ApiResponse;
import bflow.gmail.dto.GmailImportResponse;
import bflow.gmail.dto.GmailStatusResponse;
import bflow.gmail.dto.GmailSyncRequest;
import bflow.gmail.dto.GmailSyncResponse;
import bflow.gmail.service.GmailIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations/gmail")
@RequiredArgsConstructor
@Tag(name = "Gmail Integration", description = "Read-only Gmail transaction import from attachments and transactional email bodies")
public class GmailIntegrationController {
    private final GmailIntegrationService integrationService;
    private final CurrentUserService currentUserService;
    private final MessageService messageService;

    @GetMapping("/status")
    @Operation(summary = "Get the current user's Gmail connection status")
    public ResponseEntity<ApiResponse<GmailStatusResponse>> status(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(messageService.get("gmail.status.retrieved"),
                integrationService.status(currentUserService.getCurrentUserId(authentication)),
                "/api/v1/integrations/gmail/status"));
    }

    @GetMapping("/connect")
    @Operation(summary = "Create a separate Gmail read-only OAuth consent URL")
    public ResponseEntity<ApiResponse<String>> connect(Authentication authentication) {
        String url = integrationService.authorizationUrl(
                currentUserService.getCurrentUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(messageService.get("gmail.connect.started"), url,
                "/api/v1/integrations/gmail/connect"));
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth callback; validates one-time state and stores encrypted refresh credentials")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String target = error == null
                ? integrationService.callback(code, state)
                : integrationService.callback(null, state);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target).build();
    }

    @PostMapping("/sync")
    @Operation(summary = "Discover bounded Gmail attachments and transactional email bodies, then enqueue imports")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Discovery finished; OCR jobs queued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid lookback, message limit, wallet id, or request body"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid Cognito token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Wallet is not accessible to this user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Gmail is disconnected or OAuth credentials are revoked"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Attachment exceeds the configured size limit"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Gmail synchronization rate limit exceeded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected integration failure")
    })
    public ResponseEntity<ApiResponse<GmailSyncResponse>> sync(
            Authentication authentication,
            @Valid @RequestBody GmailSyncRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.success(messageService.get("gmail.sync.queued"),
                integrationService.sync(currentUserService.getCurrentUserId(authentication), request),
                "/api/v1/integrations/gmail/sync"));
    }

    @GetMapping("/imports")
    @Operation(summary = "List this user's Gmail import results")
    public ResponseEntity<ApiResponse<List<GmailImportResponse>>> imports(
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(messageService.get("gmail.imports.retrieved"),
                integrationService.imports(currentUserService.getCurrentUserId(authentication)),
                "/api/v1/integrations/gmail/imports"));
    }

    @DeleteMapping
    @Operation(summary = "Revoke Gmail access and delete stored credentials")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Gmail disconnected"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid Cognito token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected integration failure")
    })
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        integrationService.disconnect(currentUserService.getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }
}
