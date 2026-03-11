package bflow.auth.controllers;

import bflow.auth.DTO.user.UpdateUserProfileRequest;
import bflow.auth.DTO.user.UserProfileResponse;
import bflow.auth.services.UserService;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing user profile operations.
 * Provides endpoints for retrieving, updating, and deleting user profiles.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public final class UserController {

    /** Service for user profile operations. */
    private final UserService userService;

    /**
     * Retrieves the current authenticated user's profile.
     * @param authentication the current user's authentication object.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing the user profile response.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            final Authentication authentication,
            final HttpServletRequest request
    ) {

        UUID userId = UUID.fromString(authentication.getName());

        UserProfileResponse profile = userService.getProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User profile retrieved",
                        profile,
                        request.getRequestURI()
                )
        );
    }

    /**
     * Updates the current authenticated user's profile.
     * @param authentication the current user's authentication object.
     * @param requestBody the update profile request with new data.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing the updated user's profile.
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            final Authentication authentication,
            @Valid @RequestBody final UpdateUserProfileRequest requestBody,
            final HttpServletRequest request
    ) {

        UUID userId = UUID.fromString(authentication.getName());

        UserProfileResponse updated =
                userService.updateProfile(userId, requestBody);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User profile updated",
                        updated,
                        request.getRequestURI()
                )
        );
    }

    /**
     * Deletes the current authenticated user's account (soft delete).
     * @param authentication the current user's authentication object.
     * @param request the HTTP request for path information.
     * @return a ResponseEntity containing a success response.
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            final Authentication authentication,
            final HttpServletRequest request
    ) {

        UUID userId = UUID.fromString(authentication.getName());

        userService.softDelete(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User account deleted",
                        null,
                        request.getRequestURI()
                )
        );
    }

}
