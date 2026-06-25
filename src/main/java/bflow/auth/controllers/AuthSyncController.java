package bflow.auth.controllers;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import bflow.auth.services.AuthSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthSyncController {

    /**
     * Service responsible for synchronizing Cognito users.
     */
    private final AuthSyncService authSyncService;

    /**
     * Synchronizes the authenticated Cognito user with the local database.
     *
     * @param jwt authenticated JWT token
     * @param request synchronization request payload
     * @return synchronization result
     */
    @PostMapping("/sync")
    public SyncUserResponse sync(
            @AuthenticationPrincipal final Jwt jwt,
            @RequestBody final SyncUserRequest request
    ) {
        log.debug("Auth sync initiated for sub: {}", jwt.getSubject());
        return authSyncService.synchronize(jwt, request);
    }
}
