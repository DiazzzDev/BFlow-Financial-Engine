package bflow.auth.controllers;

import bflow.auth.DTO.UserMeResponse;
import bflow.auth.services.AuthService;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller handling authentication requests like login, logout, and refresh.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Service for core auth logic. */
    private final AuthService authService;

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
        UserMeResponse response = authService.getCurrentUser(authentication);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User data retrieved successfully",
                        response,
                        request.getRequestURI()
                )
        );
    }

}
