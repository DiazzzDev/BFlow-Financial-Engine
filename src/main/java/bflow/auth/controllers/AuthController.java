package bflow.auth.controllers;

import bflow.auth.DTO.Record.SyncUserRequest;
import bflow.auth.DTO.Record.SyncUserResponse;
import bflow.auth.DTO.UserMeResponse;
import bflow.auth.entities.User;
import bflow.auth.services.AuthSyncService;
import bflow.auth.services.CurrentUserService;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    /**
     * Service responsible for synchronizing Cognito users.
     */
    private final AuthSyncService authSyncService;

    /** Service used to resolve the authenticated user. */
    private final CurrentUserService currentUserService;

    /**
     * Returns the current authenticated user's details.
     * @param authentication the security context.
     * @param request the servlet request.
     * @return user details or 401 unauthorized.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> me(
            final Authentication authentication,
            final HttpServletRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = currentUserService.getCurrentUser(authentication);

        UserMeResponse response = new UserMeResponse(
                user.getId(),
                user.getEmail(),
                List.copyOf(user.getRoles()),
                null,
                List.of(),
                null
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User data retrieved successfully",
                        response,
                        request.getRequestURI()
                )
        );
    }

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
        log.debug("Auth sync initiated");
        return authSyncService.synchronize(jwt, request);
    }
}
