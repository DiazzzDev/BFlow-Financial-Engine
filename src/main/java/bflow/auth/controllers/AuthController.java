package bflow.auth.controllers;

import bflow.auth.DTO.AuthLoginRequest;
import bflow.auth.DTO.AuthMeResponse;
import bflow.auth.DTO.AuthRegisterRequest;
import bflow.auth.DTO.Record.ForgotPasswordRequest;
import bflow.auth.DTO.Record.RefreshRotationResult;
import bflow.auth.DTO.Record.RefreshSession;
import bflow.auth.DTO.Record.ResetPasswordRequest;
import bflow.auth.entities.RefreshToken;
import bflow.auth.entities.User;
import bflow.auth.security.jwt.JwtService;
import bflow.auth.services.AuthService;
import bflow.auth.services.PasswordResetService;
import bflow.auth.services.ServiceRefreshToken;
import bflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    /** Service for JWT generation and validation. */
    private final JwtService jwtService;
    /** Service for managing refresh tokens. */
    private final ServiceRefreshToken serviceRefreshToken;

    /** Service for password reset operations. */
    private final PasswordResetService passwordResetService;

    /** Total seconds in one day. */
    private static final int SECONDS_IN_A_DAY = 86400;
    /** Maximum validity of a cookie in days. */
    private static final int MAX_COOKIE_DAYS = 14;
    /** Refresh token Time To Live. */
    private static final long REFRESH_COOKIE_MAX_AGE =
            (long) MAX_COOKIE_DAYS * SECONDS_IN_A_DAY;

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

        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                roles
        );

        String rawRefreshToken = UUID.randomUUID().toString();
        serviceRefreshToken.create(user.getId(), rawRefreshToken);

        setCookie(response,
                "access_token",
                accessToken,
                jwtService.getAccessTokenTtlSeconds(), "/");

        setCookie(response,
                "refresh_token",
                rawRefreshToken,
                REFRESH_COOKIE_MAX_AGE,
                "/");

        return ResponseEntity.ok().build();
    }

    /**
     * Logs out the user and clears authentication cookies.
     * @param refreshToken the current refresh token from cookies.
     * @param response the servlet response.
     * @return a success response.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refresh_token", required = false)
            final String refreshToken,
            final HttpServletResponse response
    ) {
        if (refreshToken != null) {
            serviceRefreshToken.validateAndRotate(refreshToken);
        }

        clearCookie(response, "access_token", "/");
        clearCookie(response, "refresh_token", "/");

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

        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                roles
        );

        String rawRefreshToken = UUID.randomUUID().toString();
        serviceRefreshToken.create(user.getId(), rawRefreshToken);

        setCookie(response,
                "access_token",
                accessToken,
                jwtService.getAccessTokenTtlSeconds(), "/");

        setCookie(response,
                "refresh_token",
                rawRefreshToken,
                REFRESH_COOKIE_MAX_AGE,
                "/");

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
     * Rotates the refresh token and generates a new access token.
     * @param refreshToken current refresh token.
     * @param response servlet response.
     * @return success or unauthorized status.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(value = "refresh_token", required = false)
            final String refreshToken,
            final HttpServletResponse response
    ) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RefreshRotationResult result = serviceRefreshToken.rotate(refreshToken);

        User user = authService.findById(result.userId());
        List<String> roles = authService.getRoles(user);

        String newAccessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                roles
        );

        setCookie(response,
                "access_token",
                newAccessToken,
                jwtService.getAccessTokenTtlSeconds(), "/");
        setCookie(response,
                "refresh_token",
                result.newRefreshToken(),
                REFRESH_COOKIE_MAX_AGE,
                "/");

        return ResponseEntity.ok().build();
    }

    /**
     * Lists active refresh sessions for the user.
     * @param rawToken the current refresh token.
     * @return list of active sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<RefreshSession>> sessions(
            @CookieValue("refresh_token") final String rawToken
    ) {
        RefreshToken current = serviceRefreshToken.validate(rawToken);

        List<RefreshSession> sessions = serviceRefreshToken.listActiveSessions(
                        current.getUserId(),
                        current.getId()
        );

        return ResponseEntity.ok(sessions);
    }

    /**
     * Initiates the forgot password flow by sending a reset email.
     * @param request the forgot password request containing the email.
     * @return a response indicating if the account exists.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            final @Valid @RequestBody ForgotPasswordRequest request
    ) {

        passwordResetService.forgotPassword(request);

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "If the account exists, a recovery email has been sent."
                )
        );
    }

    /**
     * Resets the user password using a valid reset token.
     * @param request the reset password request with token and new password.
     * @return a success response.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            final @Valid @RequestBody ResetPasswordRequest request
    ) {

        passwordResetService.resetPassword(request);

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Password updated successfully"
                )
        );
    }

    /**
     * Internal utility to set a cookie on the response.
     * @param res servlet response.
     * @param name cookie name.
     * @param value cookie value.
     * @param maxAge max age in seconds.
     * @param path cookie path.
     */
    private void setCookie(
            final HttpServletResponse res,
            final String name,
            final String value,
            final long maxAge,
            final String path
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .path(path)
                .sameSite("None")
                .maxAge(maxAge)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Internal utility to clear a specific cookie.
     * @param res servlet response.
     * @param name cookie name.
     * @param path cookie path.
     */
    private void clearCookie(
            final HttpServletResponse res,
            final String name,
            final String path
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(path)
                .maxAge(0)
                .build();

        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
