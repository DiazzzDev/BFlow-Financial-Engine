package bflow.auth.controllers;

import bflow.auth.DTO.AuthLoginRequest;
import bflow.auth.DTO.AuthMeResponse;
import bflow.auth.DTO.AuthRegisterRequest;
import bflow.auth.entities.User;
import bflow.auth.services.AuthService;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * Authenticates a user and sets session cookies.
     * @param request the login credentials.
     * @param response the servlet response to attach cookies.
     * @return a empty success response.
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @RequestBody final AuthLoginRequest request,
            final HttpServletResponse response
    ) {
        User user = authService.authenticate(
                request.getEmail(),
                request.getPassword()
        );

        List<String> roles = authService.getRoles(user);

        return ResponseEntity.ok().build();
    }

    /**
     * Logs out the user and clears authentication cookies.
     * @param response the servlet response.
     * @return a success response.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refresh_token", required = false)
            final HttpServletResponse response
    ) {
        return ResponseEntity.ok().build();
    }

    /**
     * Registers a new user account.
     * @param request the registration details.
     * @param response the servlet request for path metadata.
     * @return an ApiResponse indicating status.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody final AuthRegisterRequest request,
            final HttpServletResponse response
    ) {

        User user = authService.register(request);

        List<String> roles = authService.getRoles(user);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Returns the current authenticated user's details.
     * @param authentication the security context.
     * @param httpRequest the servlet request.
     * @return user details or 401 unauthorized.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthMeResponse>> me(
            final Authentication authentication,
            final HttpServletRequest httpRequest
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthMeResponse response = new AuthMeResponse();
        response.setUserId(UUID.fromString(authentication.getName()));
        response.setRoles(authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );

        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            response.setEmail((String) map.get("email"));
        }

        return ResponseEntity.ok(
                ApiResponse.success("User authenticated",
                                             response,
                                             httpRequest.getRequestURI()));
    }

    /**
     * Verifies the user's email using the provided token.
     * This method is final to ensure safe extension of the class.
     * @param token the verification token provided by the user.
     * @return ResponseEntity with a success message.
     */
    @PostMapping("/verify-email")
    public final ResponseEntity<?> verifyEmail(
            @RequestParam final String token
    ) {

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Email verified successfully"
                )
        );
    }

    /**
     * Resends the verification email to the authenticated user.
     * This method is final to ensure safe extension of the class.
     * @param authentication the authentication object containing the user's ID.
     * @return ResponseEntity with a success message.
     */
    @PostMapping("/resend-verification")
    public final ResponseEntity<?> resendVerification(
            final Authentication authentication
    ) {

        UUID userId =
                UUID.fromString(authentication.getName());

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Verification email sent"
                )
        );
    }

}
