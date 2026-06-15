package bflow.auth.controllers;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import bflow.auth.services.AuthSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
public class AuthSyncController {

    private final AuthSyncService authSyncService;

    @PostMapping("/sync")
    public SyncUserResponse sync(

            @AuthenticationPrincipal Jwt jwt,

            @org.springframework.web.bind.annotation.RequestBody SyncUserRequest request

    ) {
        return authSyncService.synchronize(jwt, request);
    }
}