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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            Authentication authentication,
            HttpServletRequest request
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

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileRequest requestBody,
            HttpServletRequest request
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

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            Authentication authentication,
            HttpServletRequest request
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
